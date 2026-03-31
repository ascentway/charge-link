"""
opencharge_fetcher.py
Fetches Indian EV station data from OpenChargeMap API.

Free API key: https://openchargemap.org/site/develop/api
Takes 30 seconds — just enter your email on that page.
"""

import time
import logging
from typing import Optional

import requests
from tenacity import (
    retry, stop_after_attempt,
    wait_exponential, retry_if_exception_type,
    before_sleep_log
)

from config import (
    OCM_BASE_URL, OPENCHARGE_API_KEY, OCM_COUNTRY_CODE,
    OCM_CONNECTOR_MAP, NETWORK_SLUG_MAP,
    REQUEST_DELAY_SECONDS, SEARCH_RADIUS_KM,
    MAX_RESULTS_PER_CITY, INDIA_CITIES,
)

log = logging.getLogger(__name__)


# ── API fetch with auto-retry on network errors ───────────────────
@retry(
    retry=retry_if_exception_type((requests.RequestException, requests.Timeout)),
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=2, max=15),
    before_sleep=before_sleep_log(log, logging.WARNING),
)
def _fetch_city(lat: float, lng: float) -> list[dict]:
    """Fetch stations within SEARCH_RADIUS_KM of a coordinate."""
    params = {
        "key":             OPENCHARGE_API_KEY,
        "output":          "json",
        "countrycode":     OCM_COUNTRY_CODE,
        "latitude":        lat,
        "longitude":       lng,
        "distance":        SEARCH_RADIUS_KM,
        "distanceunit":    "KM",
        "maxresults":      MAX_RESULTS_PER_CITY,
        "compact":         True,
        "verbose":         False,
        "includecomments": False,
        "statustypeid":    "0,10,20,30,50,75,100,200",  # all statuses
    }
    resp = requests.get(
        f"{OCM_BASE_URL}/poi",
        params=params,
        timeout=30,
        headers={"User-Agent": "ChargeLinK-Scraper/1.0"},
    )
    resp.raise_for_status()
    data = resp.json()

    if not isinstance(data, list):
        log.warning("Unexpected response type from OCM: %s", type(data))
        return []

    return data


# ── Connector type mapping ────────────────────────────────────────
def _map_connector(conn_type_id: Optional[int]) -> str:
    """Map OCM connection type ID to ChargeLinK connector string."""
    if conn_type_id is None:
        return "Type2"  # safe default — most common in India
    return OCM_CONNECTOR_MAP.get(conn_type_id, "Type2")


# ── Operator → network slug mapping ──────────────────────────────
def _map_network_slug(operator_title: Optional[str]) -> Optional[str]:
    """Fuzzy-match operator name to known network slug."""
    if not operator_title:
        return None
    lower = operator_title.lower().strip()
    for keyword, slug in NETWORK_SLUG_MAP.items():
        if keyword in lower:
            return slug
    return None


# ── Parse one raw OCM POI into our DB shape ───────────────────────
def _parse_station(raw: dict) -> Optional[dict]:
    """
    Transform a raw OpenChargeMap POI dict into the shape
    our Supabase uploader expects.
    Returns None if the record is missing coordinates (unusable).
    """
    try:
        addr  = raw.get("AddressInfo") or {}
        conns = raw.get("Connections") or []
        op    = raw.get("OperatorInfo") or {}

        lat = addr.get("Latitude")
        lng = addr.get("Longitude")

        # Skip stations with no coordinates — can't place on map
        if lat is None or lng is None:
            return None

        # Skip stations clearly outside India
        # (OCM countrycode filter is not 100% reliable)
        lat_f, lng_f = float(lat), float(lng)
        if not (6.0 <= lat_f <= 37.0 and 68.0 <= lng_f <= 97.5):
            return None

        # Build charger list from connections
        chargers = []
        seen_codes: set[str] = set()

        for conn in conns:
            # Get connection type ID from either flat or nested format
            conn_type_id = (
                conn.get("ConnectionTypeID")
                or (conn.get("ConnectionType") or {}).get("ID")
            )

            # Build a unique code for this charger
            conn_id = conn.get("ID", "")
            code    = f"CP-{conn_id}"

            # Skip duplicate connection entries
            if code in seen_codes:
                continue
            seen_codes.add(code)

            # Determine AC vs DC from CurrentTypeID
            # OCM: 10=AC (1-Phase), 20=AC (3-Phase), 30=DC
            current_type_id = conn.get("CurrentTypeID")
            current_type    = "AC" if current_type_id in (10, 20) else "DC"

            power_kw = conn.get("PowerKW")

            chargers.append({
                "charger_code":   code,
                "connector_type": _map_connector(conn_type_id),
                "power_kw":       float(power_kw) if power_kw else 0.0,
                "current_type":   current_type,
                "current_status": "unknown",   # will be updated by crowdsource
                "status_source":  "scraped",
                "is_active":      True,
            })

        # A station with zero charger connections is useless — skip it
        if not chargers:
            return None

        return {
            "external_id":  str(raw.get("ID", "")),
            "name":         (addr.get("Title") or "EV Charging Station").strip(),
            "address":      (addr.get("AddressLine1") or "").strip(),
            "city":         (addr.get("Town") or "").strip(),
            "state":        (addr.get("StateOrProvince") or "").strip(),
            "pincode":      (addr.get("Postcode") or "").strip(),
            "lat":          lat_f,
            "lng":          lng_f,
            "data_source":  "opencharge_map",
            "is_verified":  False,
            "network_slug": _map_network_slug(op.get("Title")),
            "chargers":     chargers,
        }

    except Exception as exc:
        log.warning("Failed to parse OCM record ID=%s: %s", raw.get("ID"), exc)
        return None


# ── Main fetch function ───────────────────────────────────────────
def fetch_all_india_stations(dry_run: bool = False) -> list[dict]:
    """
    Fetch EV stations across all Indian cities from OpenChargeMap.

    Args:
        dry_run: If True, only fetch from the first 3 cities (for testing).

    Returns:
        List of parsed station dicts, deduplicated by external_id.
    """
    cities = INDIA_CITIES[:3] if dry_run else INDIA_CITIES

    seen_ids:     set[str]  = set()
    all_stations: list[dict] = []
    total_raw    = 0

    for i, city in enumerate(cities, 1):
        log.info("[%d/%d] Fetching near %s (%.4f, %.4f) radius=%dkm ...",
                 i, len(cities), city["name"],
                 city["lat"], city["lng"], SEARCH_RADIUS_KM)
        try:
            raw_list  = _fetch_city(city["lat"], city["lng"])
            total_raw += len(raw_list)
            new_count  = 0

            for raw in raw_list:
                ext_id = str(raw.get("ID", ""))
                if not ext_id or ext_id in seen_ids:
                    continue
                seen_ids.add(ext_id)

                station = _parse_station(raw)
                if station:
                    all_stations.append(station)
                    new_count += 1

            log.info("  → %d raw POIs, %d new unique stations added",
                     len(raw_list), new_count)

        except Exception as exc:
            log.error("  ✗ Failed to fetch %s after retries: %s", city["name"], exc)

        # Polite delay between city requests
        if i < len(cities):
            time.sleep(REQUEST_DELAY_SECONDS)

    total_chargers = sum(len(s["chargers"]) for s in all_stations)

    log.info("")
    log.info("━" * 50)
    log.info("Fetch complete")
    log.info("  Cities processed  : %d", len(cities))
    log.info("  Total raw POIs    : %d", total_raw)
    log.info("  Unique stations   : %d", len(all_stations))
    log.info("  Total chargers    : %d", total_chargers)
    log.info("━" * 50)

    return all_stations
