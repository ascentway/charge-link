"""
main.py — ChargeLinK Station Data Scraper
=========================================

COMMANDS:
  python main.py              — full run, all cities, uploads to Supabase
  python main.py --dry-run    — test run, first 3 cities only, NO upload
  python main.py --verify     — check what's already in your Supabase DB

FIRST TIME SETUP:
  1. Copy .env.example to .env and fill in your 3 keys
  2. Run: python main.py --dry-run    (test without writing anything)
  3. Run: python main.py              (full run when dry-run looks good)
"""

import argparse
import logging
import sys
import time

from opencharge_fetcher import fetch_all_india_stations
from supabase_uploader  import upload_stations, get_client

# ── Logging setup ─────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-7s  %(message)s",
    datefmt="%H:%M:%S",
    handlers=[logging.StreamHandler(sys.stdout)],
)
log = logging.getLogger(__name__)


# ── Verify command ────────────────────────────────────────────────
def cmd_verify():
    """Print a summary of what's already in the Supabase DB."""
    log.info("Connecting to Supabase and checking existing data ...")
    client = get_client()

    stations_resp = client.table("stations").select("id", count="exact").execute()
    chargers_resp = client.table("chargers").select("id", count="exact").execute()
    networks_resp = client.table("networks").select("id, name, has_live_api").execute()

    station_count = stations_resp.count or 0
    charger_count = chargers_resp.count or 0
    networks      = networks_resp.data or []

    print("")
    print("━" * 50)
    print("  ChargeLinK — Supabase DB status")
    print("━" * 50)
    print(f"  Stations in DB  : {station_count}")
    print(f"  Chargers in DB  : {charger_count}")
    print(f"  Networks seeded : {len(networks)}")
    print("")
    print("  Networks:")
    for n in networks:
        api_status = "live API" if n["has_live_api"] else "crowdsourced"
        print(f"    • {n['name']:<25} {api_status}")
    print("━" * 50)

    if station_count == 0:
        print("")
        print("  DB is empty. Run schema.sql + seed.sql in Supabase")
        print("  SQL Editor first, then run: python main.py")


# ── Dry run command ───────────────────────────────────────────────
def cmd_dry_run():
    """Fetch from 3 cities, print results, do NOT write to Supabase."""
    log.info("DRY RUN — fetching from first 3 cities, no DB writes")
    stations = fetch_all_india_stations(dry_run=True)

    if not stations:
        log.error("No stations fetched. Check OPENCHARGE_API_KEY in .env")
        return

    print("")
    print("━" * 60)
    print(f"  Dry run complete — {len(stations)} stations fetched")
    print("━" * 60)
    print("")
    print("  Sample stations:")
    for s in stations[:8]:
        charger_count = len(s["chargers"])
        connectors    = list({c["connector_type"] for c in s["chargers"]})
        print(f"  • {s['name'][:40]:<42} {s['city']:<15} "
              f"{charger_count} charger(s) [{', '.join(connectors)}]")

    if len(stations) > 8:
        print(f"  ... and {len(stations) - 8} more")

    print("")
    print("  Looks good? Run the full scraper:")
    print("  python main.py")
    print("━" * 60)


# ── Full run command ──────────────────────────────────────────────
def cmd_full_run():
    """Fetch all cities and upload to Supabase."""
    start = time.time()

    log.info("━" * 55)
    log.info("  ChargeLinK Scraper — full India run")
    log.info("━" * 55)

    # Step 1 — fetch
    log.info("")
    log.info("[1/2] Fetching EV stations from OpenChargeMap ...")
    stations = fetch_all_india_stations(dry_run=False)

    if not stations:
        log.error("No stations fetched. Check OPENCHARGE_API_KEY in .env")
        sys.exit(1)

    # Step 2 — upload
    log.info("")
    log.info("[2/2] Uploading %d stations to Supabase ...", len(stations))
    result = upload_stations(stations)

    elapsed = round(time.time() - start, 1)

    print("")
    print("━" * 55)
    print("  Scraper complete!")
    print("━" * 55)
    print(f"  Time taken        : {elapsed}s")
    print(f"  Stations upserted : {result['stations_upserted']}")
    print(f"  Chargers upserted : {result['chargers_upserted']}")
    print(f"  Errors            : {result['errors']}")
    print("━" * 55)
    print("")
    print("  Next steps:")
    print("  1. Open Supabase dashboard → Table Editor → stations")
    print("     Verify rows are there and lat/lng look right")
    print("  2. Dashboard → Database → Replication")
    print("     Enable Realtime on: chargers, status_reports, bookings")
    print("  3. Start the backend: cd backend && ./mvnw spring-boot:run")
    print("━" * 55)


# ── Entry point ───────────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser(
        description="ChargeLinK — EV station data scraper"
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Fetch from 3 cities only, print results, do NOT write to DB",
    )
    parser.add_argument(
        "--verify",
        action="store_true",
        help="Check what's already in your Supabase DB",
    )
    args = parser.parse_args()

    if args.verify:
        cmd_verify()
    elif args.dry_run:
        cmd_dry_run()
    else:
        cmd_full_run()


if __name__ == "__main__":
    main()
