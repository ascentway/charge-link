"""
main.py — ChargeLinK Multi-Source Station Scraper
==================================================

Sources (run in combination for maximum coverage):
  1. OpenChargeMap  — free, good global coverage, ~608 India stations
  2. Google Places  — free tier, best India coverage (Tata, ChargeZone, Statiq)
  3. OpenStreetMap  — free, no key, crowd-mapped supplemental data

COMMANDS:
  python main.py              — all sources, full run, uploads to Supabase
  python main.py --dry-run    — first 3 cities, NO upload (test your keys)
  python main.py --verify     — check what is already in your Supabase DB
  python main.py --ocm-only   — only OpenChargeMap (original behaviour)
  python main.py --google-only — only Google Places
  python main.py --osm-only   — only OpenStreetMap
"""

import argparse
import logging
import sys
import time

from opencharge_fetcher  import fetch_all_india_stations
from supabase_uploader   import upload_stations, get_client
from config              import GOOGLE_PLACES_API_KEY

log = logging.getLogger(__name__)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-7s  %(message)s",
    datefmt="%H:%M:%S",
    handlers=[logging.StreamHandler(sys.stdout)],
)


def cmd_verify():
    client = get_client()
    s = client.table("stations").select("id", count="exact").execute()
    c = client.table("chargers").select("id", count="exact").execute()
    n = client.table("networks").select("id, name, has_live_api").execute()

    print("")
    print("━" * 55)
    print("  ChargeLinK — Supabase DB status")
    print("━" * 55)
    print(f"  Stations : {s.count or 0}")
    print(f"  Chargers : {c.count or 0}")
    print(f"  Networks : {len(n.data or [])}")
    if n.data:
        print("")
        for row in n.data:
            print(f"    • {row['name']}")
    print("━" * 55)


def _collect_all(dry_run: bool = False,
                 use_ocm: bool = True,
                 use_google: bool = True,
                 use_osm: bool = True) -> list[dict]:
    """Fetch from all enabled sources, deduplicate by lat/lng proximity."""
    all_stations: list[dict] = []
    seen_ext_ids: set[str]   = set()

    # ── 1. OpenChargeMap ──────────────────────────────────────────
    if use_ocm:
        print("")
        print("━" * 55)
        print("  Source 1/3 — OpenChargeMap")
        print("━" * 55)
        ocm = fetch_all_india_stations(dry_run=dry_run)
        for s in ocm:
            if s["external_id"] not in seen_ext_ids:
                seen_ext_ids.add(s["external_id"])
                all_stations.append(s)
        print(f"  OCM contributed: {len(ocm)} stations")

    # ── 2. Google Places ──────────────────────────────────────────
    if use_google:
        if not GOOGLE_PLACES_API_KEY:
            print("")
            print("  Source 2/3 — Google Places SKIPPED")
            print("  Add GOOGLE_PLACES_API_KEY to .env to enable")
            print("  (console.cloud.google.com → Places API New → free key)")
        else:
            print("")
            print("━" * 55)
            print("  Source 2/3 — Google Places API (New)")
            print("━" * 55)
            from google_places_fetcher import fetch_google_places_stations
            goog = fetch_google_places_stations(dry_run=dry_run)
            new_from_google = 0
            for s in goog:
                if s["external_id"] not in seen_ext_ids:
                    seen_ext_ids.add(s["external_id"])
                    all_stations.append(s)
                    new_from_google += 1
            print(f"  Google contributed: {new_from_google} new stations")

    # ── 3. OpenStreetMap ─────────────────────────────────────────
    if use_osm and not dry_run:
        print("")
        print("━" * 55)
        print("  Source 3/3 — OpenStreetMap (Overpass API)")
        print("━" * 55)
        from osm_fetcher import fetch_osm_stations
        osm = fetch_osm_stations()
        new_from_osm = 0
        for s in osm:
            if s["external_id"] not in seen_ext_ids:
                seen_ext_ids.add(s["external_id"])
                all_stations.append(s)
                new_from_osm += 1
        print(f"  OSM contributed: {new_from_osm} new stations")
    elif use_osm and dry_run:
        print("\n  Source 3/3 — OpenStreetMap skipped in dry-run mode")

    return all_stations


def cmd_run(dry_run=False, use_ocm=True, use_google=True, use_osm=True):
    start = time.time()

    print("")
    print("━" * 55)
    print("  ChargeLinK Multi-Source Scraper")
    print("━" * 55)

    stations = _collect_all(
        dry_run=dry_run,
        use_ocm=use_ocm,
        use_google=use_google,
        use_osm=use_osm,
    )

    print("")
    print(f"  Total unique stations collected: {len(stations)}")
    total_chargers = sum(len(s["chargers"]) for s in stations)
    print(f"  Total chargers across all stations: {total_chargers}")

    if dry_run:
        print("")
        print("  DRY RUN — no DB writes. Sample:")
        for s in stations[:8]:
            connectors = list({c["connector_type"] for c in s["chargers"]})
            print(f"    • {s['name'][:38]:<40} {s['city']:<15}"
                  f" {len(s['chargers'])} charger(s) [{', '.join(connectors)}]"
                  f" [{s['data_source']}]")
        if len(stations) > 8:
            print(f"    ... and {len(stations) - 8} more")
        print("")
        print("  Run without --dry-run to upload to Supabase")
        return

    # Upload
    print("")
    print(f"  Uploading to Supabase ...")
    result = upload_stations(stations)
    elapsed = round(time.time() - start, 1)

    print("")
    print("━" * 55)
    print(f"  Done in {elapsed}s")
    print(f"  Stations upserted : {result['stations_upserted']}")
    print(f"  Chargers upserted : {result['chargers_upserted']}")
    print(f"  Errors            : {result['errors']}")
    print("━" * 55)
    print("")
    print("  Next:")
    print("  python main.py --verify       check row counts")
    print("  cd backend && ./mvnw spring-boot:run   start API")
    print("━" * 55)


def main():
    parser = argparse.ArgumentParser(description="ChargeLinK station scraper")
    parser.add_argument("--dry-run",     action="store_true")
    parser.add_argument("--verify",      action="store_true")
    parser.add_argument("--ocm-only",    action="store_true")
    parser.add_argument("--google-only", action="store_true")
    parser.add_argument("--osm-only",    action="store_true")
    args = parser.parse_args()

    if args.verify:
        cmd_verify(); return

    use_ocm    = not (args.google_only or args.osm_only)
    use_google = not (args.ocm_only    or args.osm_only)
    use_osm    = not (args.ocm_only    or args.google_only)

    cmd_run(
        dry_run=args.dry_run,
        use_ocm=use_ocm,
        use_google=use_google,
        use_osm=use_osm,
    )


if __name__ == "__main__":
    main()
