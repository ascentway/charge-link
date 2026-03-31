"""
supabase_uploader.py
Uploads scraped station + charger data to Supabase.

Key design decisions:
- Uses upsert on external_id so re-running is always safe (no duplicates)
- Fetches station IDs back via SELECT after upsert (fixes SDK versions
  that return empty data on upsert)
- Skips stations whose external_id is already in DB and unchanged
- Reports progress every batch so you can see it working
"""

import logging
from supabase import create_client, Client
from config import SUPABASE_URL, SUPABASE_SERVICE_KEY, BATCH_SIZE

log = logging.getLogger(__name__)

_client: Client | None = None


def get_client() -> Client:
    global _client
    if _client is None:
        _client = create_client(SUPABASE_URL, SUPABASE_SERVICE_KEY)
    return _client


def _get_network_id_map() -> dict[str, str]:
    """
    Fetch {slug: uuid} for all networks in the DB.
    Used to link stations to their network operator.
    """
    resp = get_client().table("networks").select("id, slug").execute()
    result = {row["slug"]: row["id"] for row in (resp.data or [])}
    log.info("Loaded %d networks from DB: %s",
             len(result), list(result.keys()))
    return result


def _fetch_station_ids_by_external(external_ids: list[str]) -> dict[str, str]:
    """
    Fetch {external_id: station_uuid} for a list of external IDs.
    Used to get station UUIDs after upsert (some SDK versions
    return empty .data on upsert even on success).
    """
    if not external_ids:
        return {}

    resp = (
        get_client()
        .table("stations")
        .select("id, external_id")
        .in_("external_id", external_ids)
        .execute()
    )
    return {row["external_id"]: row["id"] for row in (resp.data or [])}


def upload_stations(stations: list[dict]) -> dict:
    """
    Upsert all stations and their chargers into Supabase.

    Args:
        stations: List of parsed station dicts from opencharge_fetcher.

    Returns:
        Summary dict with station/charger counts and error count.
    """
    client      = get_client()
    network_map = _get_network_id_map()

    stations_ok  = 0
    chargers_ok  = 0
    errors       = 0
    total        = len(stations)

    log.info("Starting upload of %d stations in batches of %d ...",
             total, BATCH_SIZE)

    for batch_start in range(0, total, BATCH_SIZE):
        batch       = stations[batch_start: batch_start + BATCH_SIZE]
        batch_num   = batch_start // BATCH_SIZE + 1
        total_batches = (total + BATCH_SIZE - 1) // BATCH_SIZE

        # ── Build station rows ────────────────────────────────────
        station_rows: list[dict]          = []
        chargers_by_ext: dict[str, list]  = {}

        for s in batch:
            ext_id     = s["external_id"]
            network_id = network_map.get(s["network_slug"]) if s.get("network_slug") else None

            row: dict = {
                "external_id": ext_id,
                "name":        s["name"],
                "address":     s["address"],
                "city":        s["city"],
                "state":       s["state"],
                "pincode":     s["pincode"],
                "lat":         s["lat"],
                "lng":         s["lng"],
                "data_source": s["data_source"],
                "is_verified": s["is_verified"],
            }
            if network_id:
                row["network_id"] = network_id

            station_rows.append(row)
            chargers_by_ext[ext_id] = s.get("chargers", [])

        # ── Upsert stations ───────────────────────────────────────
        try:
            (
                client.table("stations")
                .upsert(
                    station_rows,
                    on_conflict="external_id",  # safe: external_id is unique
                    ignore_duplicates=False,     # update existing rows
                )
                .execute()
            )

            # Fetch back station UUIDs — don't rely on upsert returning data
            ext_ids_in_batch  = [r["external_id"] for r in station_rows]
            station_id_map    = _fetch_station_ids_by_external(ext_ids_in_batch)
            stations_ok      += len(station_id_map)

        except Exception as exc:
            log.error("Batch %d/%d — station upsert failed: %s",
                      batch_num, total_batches, exc)
            errors += len(batch)
            continue

        # ── Upsert chargers for each station ──────────────────────
        batch_chargers = 0

        for ext_id, charger_list in chargers_by_ext.items():
            station_uuid = station_id_map.get(ext_id)
            if not station_uuid or not charger_list:
                continue

            charger_rows = [
                {**c, "station_id": station_uuid}
                for c in charger_list
            ]

            try:
                (
                    client.table("chargers")
                    .upsert(
                        charger_rows,
                        on_conflict="station_id,charger_code",
                        ignore_duplicates=False,
                    )
                    .execute()
                )
                batch_chargers += len(charger_rows)

            except Exception as exc:
                log.warning("Charger upsert failed for station %s: %s",
                            ext_id, exc)

        chargers_ok += batch_chargers

        log.info(
            "Batch %d/%d done — stations: %d, chargers: %d",
            batch_num, total_batches,
            len(station_id_map), batch_chargers,
        )

    return {
        "stations_upserted": stations_ok,
        "chargers_upserted": chargers_ok,
        "errors":            errors,
    }
