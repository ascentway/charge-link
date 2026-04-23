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
import math
from concurrent.futures import ThreadPoolExecutor, as_completed

from opencharge_fetcher  import fetch_all_india_stations
from supabase_uploader   import upload_stations, get_client
from config              import GOOGLE_PLACES_API_KEY, INDIA_CITIES, REQUEST_DELAY_SECONDS

log = logging.getLogger(__name__)

def haversine(lat1, lon1, lat2, lon2):
    """Calculate distance in meters between two points."""
    R = 6371000 # Earth radius in meters
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2)**2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2)**2
    return 2 * R * math.atan2(math.sqrt(a), math.sqrt(1 - a))

def _are_close(s1, s2, threshold_meters=50):
    return haversine(s1["lat"], s1["lng"], s2["lat"], s2["lng"]) <= threshold_meters

def _collect_all(dry_run: bool = False,
                 use_ocm: bool = True,
                 use_google: bool = True,
                 use_osm: bool = True) -> list[dict]:
    """Fetch from all enabled sources, deduplicate by ID and spatial proximity."""
    all_stations: list[dict] = []
    seen_ext_ids: set[str]   = set()

    # ── 1. OpenChargeMap (Sequential - safe for rate limits) ──────
    if use_ocm:
        print("\n" + "━" * 55 + "\n  Source 1/3 — OpenChargeMap\n" + "━" * 55)
        ocm = fetch_all_india_stations(dry_run=dry_run)
        for s in ocm:
            seen_ext_ids.add(s["external_id"])
            all_stations.append(s)
        print(f"  OCM contributed: {len(ocm)} stations")

    # ── 2. Google Places (DEACTIVATED BY USER REQUEST 2026-04-07) ──
    # To re-activate:
    # 1. Enable "Places API (New)" in Google Cloud Console
    # 2. Fix 403 API_KEY_SERVICE_BLOCKED restriction
    # 3. Remove the 'use_google = False' line below
    use_google = False
    if use_google:
        if not GOOGLE_PLACES_API_KEY:
            print("\n  Source 2/3 — Google Places SKIPPED (No API Key)")
        else:
            print("\n" + "━" * 55 + "\n  Source 2/3 — Google Places (Concurrent)\n" + "━" * 55)
            from google_places_fetcher import _fetch_city, _parse_place

            cities = INDIA_CITIES[:3] if dry_run else INDIA_CITIES
            goog_raw = []

            with ThreadPoolExecutor(max_workers=5) as executor:
                future_to_city = {executor.submit(_fetch_city, c["lat"], c["lng"]): c for c in cities}
                for future in as_completed(future_to_city):
                    city = future_to_city[future]
                    try:
                        places = future.result()
                        goog_raw.extend(places)
                        log.info(f"  ✓ {city['name']:<15} : {len(places)} places")
                    except Exception as e:
                        log.error(f"  ✗ {city['name']:<15} : Failed: {e}")

            new_from_google = 0
            for p in goog_raw:
                station = _parse_place(p)
                if not station: continue

                ext_id = station["external_id"]
                if ext_id in seen_ext_ids: continue

                # Spatial check: is it already added by OCM?
                is_duplicate = any(_are_close(station, existing) for existing in all_stations)
                if not is_duplicate:
                    seen_ext_ids.add(ext_id)
                    all_stations.append(station)
                    new_from_google += 1

            print(f"  Google contributed: {new_from_google} unique new stations")

    # ── 3. OpenStreetMap (Supplemental) ──────────────────────────
    if use_osm and not dry_run:
        print("\n" + "━" * 55 + "\n  Source 3/3 — OpenStreetMap\n" + "━" * 55)
        from osm_fetcher import fetch_osm_stations
        osm = fetch_osm_stations()
        new_from_osm = 0
        for s in osm:
            if s["external_id"] in seen_ext_ids: continue
            if any(_are_close(s, existing) for existing in all_stations): continue

            seen_ext_ids.add(s["external_id"])
            all_stations.append(s)
            new_from_osm += 1
        print(f"  OSM contributed: {new_from_osm} unique new stations")

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


def cmd_verify():
    """Check how many stations and chargers are currently in Supabase."""
    client = get_client()

    print("\n" + "━" * 55)
    print("  Supabase Database Verification")
    print("━" * 55)

    try:
        # Count stations
        st_res = client.table("stations").select("id", count="exact").execute()
        st_count = st_res.count

        # Count chargers
        ch_res = client.table("chargers").select("id", count="exact").execute()
        ch_count = ch_res.count

        # Count networks
        nw_res = client.table("networks").select("id", count="exact").execute()
        nw_count = nw_res.count

        print(f"  Networks : {nw_count}")
        print(f"  Stations : {st_count}")
        print(f"  Chargers : {ch_count}")

        if st_count > 0:
            # Sample check
            latest = client.table("stations").select("name, city, data_source").order("created_at", desc=True).limit(5).execute()
            print("\n  Latest 5 stations:")
            for s in latest.data:
                print(f"    • {s['name'][:30]:<32} | {s['city']:<15} | {s['data_source']}")

    except Exception as e:
        print(f"  Error connecting to Supabase: {e}")

    print("━" * 55 + "\n")


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
