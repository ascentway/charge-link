"""
config.py — ChargeLinK Scraper Configuration
All constants live here. Only this file needs changing to tune the scraper.
"""

import os
from dotenv import load_dotenv

load_dotenv()

# ── Supabase credentials (from your .env file) ───────────────────
SUPABASE_URL         = os.environ["SUPABASE_URL"]
SUPABASE_SERVICE_KEY = os.environ["SUPABASE_SERVICE_ROLE_KEY"]

# ── OpenChargeMap credentials ────────────────────────────────────
# Free key from: https://openchargemap.org/site/develop/api
# Takes 30 seconds to get — just enter your email, no credit card
OPENCHARGE_API_KEY   = os.environ["OPENCHARGE_API_KEY"]

# ── Scraper tuning ───────────────────────────────────────────────
BATCH_SIZE             = int(os.getenv("BATCH_SIZE", 50))
REQUEST_DELAY_SECONDS  = float(os.getenv("REQUEST_DELAY_SECONDS", 1.2))

# How many km radius to search around each city centre
# 50km catches suburbs and highway corridors between cities
SEARCH_RADIUS_KM       = int(os.getenv("SEARCH_RADIUS_KM", 50))

# Max results per city request (OCM max is 500 per call)
MAX_RESULTS_PER_CITY   = 500

# ── OpenChargeMap API ─────────────────────────────────────────────
OCM_BASE_URL     = "https://api.openchargemap.io/v3"
OCM_COUNTRY_CODE = "IN"

# ── Connector type mapping ────────────────────────────────────────
# OpenChargeMap Connection Type IDs → ChargeLinK connector names
# Full reference: https://openchargemap.org/site/develop/api
OCM_CONNECTOR_MAP: dict[int, str] = {
    # AC connectors
    1:    "Type1",      # SAE J1772 Type 1
    25:   "Type2",      # IEC 62196-2 Type 2 (Mennekes) — most common in India
    27:   "Type1",      # Type 1 (J1772) variant
    30:   "Type2",      # Type 2 socket (untethered)
    1024: "Bharat AC",  # Bharat EV AC001 — Indian domestic standard

    # DC fast chargers
    2:    "CHAdeMO",    # CHAdeMO (older Nissan, some Tata)
    32:   "CHAdeMO",    # CHAdeMO variant
    33:   "CCS1",       # CCS Type 1 (rare in India)
    1036: "CCS2",       # CCS Type 2 (Combined Charging System) — most new EVs
    1025: "Bharat DC",  # Bharat EV DC001 — Indian domestic standard

    # GB/T (Chinese standard — used by some Indian brands)
    28:   "GB/T AC",
    29:   "GB/T DC",
}

# ── Network operator mapping ──────────────────────────────────────
# Maps partial operator names from OCM → slugs in our networks table
# Add more as you discover new operators in the data
NETWORK_SLUG_MAP: dict[str, str] = {
    "tata power":     "tata-power-ev",
    "tatapower":      "tata-power-ev",
    "chargezone":     "chargezone",
    "charge zone":    "chargezone",
    "statiq":         "statiq",
    "ather":          "ather-grid",
    "bpcl":           "bpcl-ev",
    "volttic":        "volttic",
    "evre":           "evre",
    "zeon":           "zeon-charging",
    "fortum":         "chargezone",   # Fortum merged into ChargeZone
    "evpoint":        "chargezone",
}

# ── Indian cities to scrape ───────────────────────────────────────
# Ordered by EV density — best coverage first
# lat/lng = city centre, SEARCH_RADIUS_KM applies around each point
INDIA_CITIES: list[dict] = [
    # Tier 1 — highest EV density
    {"name": "Mumbai",       "lat": 19.0760,  "lng": 72.8777},
    {"name": "Delhi NCR",    "lat": 28.6139,  "lng": 77.2090},
    {"name": "Bangalore",    "lat": 12.9716,  "lng": 77.5946},
    {"name": "Hyderabad",    "lat": 17.3850,  "lng": 78.4867},
    {"name": "Pune",         "lat": 18.5204,  "lng": 73.8567},
    {"name": "Chennai",      "lat": 13.0827,  "lng": 80.2707},

    # Tier 2 — growing EV markets
    {"name": "Ahmedabad",    "lat": 23.0225,  "lng": 72.5714},
    {"name": "Surat",        "lat": 21.1702,  "lng": 72.8311},
    {"name": "Kolkata",      "lat": 22.5726,  "lng": 88.3639},
    {"name": "Jaipur",       "lat": 26.9124,  "lng": 75.7873},
    {"name": "Kochi",        "lat": 9.9312,   "lng": 76.2673},
    {"name": "Chandigarh",   "lat": 30.7333,  "lng": 76.7794},

    # Tier 3 — emerging
    {"name": "Lucknow",      "lat": 26.8467,  "lng": 80.9462},
    {"name": "Nagpur",       "lat": 21.1458,  "lng": 79.0882},
    {"name": "Indore",       "lat": 22.7196,  "lng": 75.8577},
    {"name": "Coimbatore",   "lat": 11.0168,  "lng": 76.9558},
    {"name": "Bhubaneswar",  "lat": 20.2961,  "lng": 85.8245},
    {"name": "Vadodara",     "lat": 22.3072,  "lng": 73.1812},
    {"name": "Gurgaon",      "lat": 28.4595,  "lng": 77.0266},
    {"name": "Noida",        "lat": 28.5355,  "lng": 77.3910},

    # Highway corridors — important for long-distance EV travel
    {"name": "Nashik",       "lat": 20.0059,  "lng": 73.7898},
    {"name": "Lonavala",     "lat": 18.7481,  "lng": 73.4072},  # Mumbai-Pune highway
    {"name": "Vapi",         "lat": 20.3717,  "lng": 72.9050},  # Mumbai-Surat
    {"name": "Anand",        "lat": 22.5645,  "lng": 72.9289},  # Ahmedabad-Surat
]
