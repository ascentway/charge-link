"""
google_places_fetcher.py
Fetches Indian EV charging stations from Google Places API (New).

Why Google Places over OpenChargeMap alone:
- Google has Tata Power, ChargeZone, Statiq all indexed from Google My Business
- Returns evChargeOptions: connector types, charger count, speeds
- $200 free credit/month resets → full India scan costs ~$6-10 total
- Returns businessStatus so you know if the location is permanently closed

Get your API key:
  1. Go to console.cloud.google.com
  2. Create project → Enable "Places API (New)"
  3. APIs & Services → Credentials → Create API Key
  4. Restrict key to Places API only
"""

import time
import logging
from typing import Optional

import requests
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type

from config import (
    GOOGLE_PLACES_API_KEY,
    REQUEST_DELAY_SECONDS,
    SEARCH_RADIUS_KM,
    INDIA_CITIES,
    OCM_CONNECTOR_MAP,
    NETWORK_SLUG_MAP,
)

log = logging.getLogger(__name__)

PLACES_URL = "https://places.googleapis.com/v1/places:searchNearby"

# Fields to request — only pay for what you need
FIELD_MASK = ",".join([
    "places.id",
    "places.displayName",
    "places.formattedAddress",
    "places.location",
    "places.evChargeOptions",
    "places.regularOpeningHours",
    "places.businessStatus",
    "places.rating",
    "places.googleMapsUri",
])

# Google connector type names → ChargeLinK types
GOOGLE_CONNECTOR_MAP: dict[str, str] = {
    "EV_CONNECTOR_TYPE_CCS_COMBO_1":    "CCS1",
    "EV_CONNECTOR_TYPE_CCS_COMBO_2":    "CCS2",
    "EV_CONNECTOR_TYPE_CHADEMO":        "CHAdeMO",
    "EV_CONNECTOR_TYPE_J1772":          "Type1",
    "EV_CONNECTOR_TYPE_MENNEKES":       "Type2",
    "EV_CONNECTOR_TYPE_TYPE_2":         "Type2",
    "EV_CONNECTOR_TYPE_TESLA_ROADSTER": "Tesla",
    "EV_CONNECTOR_TYPE_TESLA_S":        "Tesla",
    "EV_CONNECTOR_TYPE_TESLA_SUPERCHARGER": "Tesla",
    "EV_CONNECTOR_TYPE_GB_T_AC":        "GB/T AC",
    "EV_CONNECTOR_TYPE_GB_T_DC":        "GB/T DC",
    "EV_CONNECTOR_TYPE_UNSPECIFIED":    "Type2",  # safe default
    "EV_CONNECTOR_TYPE_OTHER":          "Type2",
}


@retry(
    retry=retry_if_exception_type((requests.RequestException, requests.Timeout)),
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=2, max=15),
)
def _fetch_city(lat: float, lng: float) -> list[dict]:
    """Fetch up to 20 EV charging stations near a coordinate via Places API."""
    headers = {
        "Content-Type":    "application/json",
        "X-Goog-Api-Key":  GOOGLE_PLACES_API_KEY,
        "X-Goog-FieldMask": FIELD_MASK,
    }
    body = {
        "includedTypes": ["electric_vehicle_charging_station"],
        "locationRestriction": {
            "circle": {
                "center": {"latitude": lat, "longitude": lng},
                "radius": float(SEARCH_RADIUS_KM * 1000),  # metres
            }
        },
        "maxResultCount": 20,  # max allowed by Google per call
    }
    resp = requests.post(PLACES_URL, json=body, headers=headers, timeout=30)
    resp.raise_for_status()
    data = resp.json()
    return data.get("places", [])


def _map_google_connector(gtype: str) -> str:
    return GOOGLE_CONNECTOR_MAP.get(gtype, "Type2")


def _map_network_from_name(name: str) -> Optional[str]:
    """Try to detect the network operator from the place name."""
    lower = name.lower()
    for keyword, slug in NETWORK_SLUG_MAP.items():
        if keyword in lower:
            return slug
    return None


def _parse_place(place: dict) -> Optional[dict]:
    """Transform a Google Places result into our DB shape."""
    try:
        # Skip permanently closed locations
        if place.get("businessStatus") == "CLOSED_PERMANENTLY":
            return None

        loc  = place.get("location", {})
        lat  = loc.get("latitude")
        lng  = loc.get("longitude")
        if lat is None or lng is None:
            return None

        name    = (place.get("displayName") or {}).get("text") or "EV Charging Station"
        address = place.get("formattedAddress") or ""

        # Parse address components (city, state) from formatted address
        # Format is usually: "Shop Name, Street, Area, City, State PIN, India"
        parts = [p.strip() for p in address.split(",")]
        # Remove "India" at end, then last = PIN+state or city
        parts = [p for p in parts if p.lower() != "india"]
        city  = parts[-2].strip() if len(parts) >= 2 else ""
        state = parts[-1].strip() if len(parts) >= 1 else ""
        # Clean PIN from city/state
        city  = " ".join(w for w in city.split() if not w.isdigit())
        state = " ".join(w for w in state.split() if not w.isdigit())

        # Parse EV charger options
        ev_opts     = place.get("evChargeOptions") or {}
        connectors  = ev_opts.get("connectorAggregation") or []
        chargers    = []

        for i, conn in enumerate(connectors):
            gtype       = conn.get("type") or "EV_CONNECTOR_TYPE_UNSPECIFIED"
            count       = conn.get("count") or 1
            max_charge  = conn.get("maxChargeRateKw") or 0.0
            available   = conn.get("availableCount")  # may be None if no live data

            # Each connector type becomes one charger row per unit
            for j in range(count):
                status = "unknown"
                if available is not None:
                    status = "available" if available > 0 else "occupied"

                chargers.append({
                    "charger_code":   f"G-{i+1}-{j+1}",
                    "connector_type": _map_google_connector(gtype),
                    "power_kw":       float(max_charge),
                    "current_type":   "AC" if max_charge <= 22 else "DC",
                    "current_status": status,
                    "status_source":  "google_places",
                    "is_active":      True,
                })

        # If no connector data, create a generic unknown charger
        # (better to have the station in DB than miss it)
        if not chargers:
            chargers.append({
                "charger_code":   "G-1",
                "connector_type": "Type2",
                "power_kw":       0.0,
                "current_type":   "AC",
                "current_status": "unknown",
                "status_source":  "google_places",
                "is_active":      True,
            })

        return {
            "external_id":  f"gp-{place.get('id', '')}",
            "name":         name.strip(),
            "address":      address,
            "city":         city,
            "state":        state,
            "pincode":      "",
            "lat":          float(lat),
            "lng":          float(lng),
            "data_source":  "google_places",
            "is_verified":  False,
            "network_slug": _map_network_from_name(name),
            "chargers":     chargers,
        }

    except Exception as exc:
        log.warning("Failed to parse Google place %s: %s",
                    place.get("id"), exc)
        return None


def fetch_google_places_stations(dry_run: bool = False) -> list[dict]:
    """
    Fetch EV stations from Google Places API across Indian cities.

    NOTE: Returns max 20 results per city (Google API limit per call).
    For dense cities, we split into sub-regions to get better coverage.
    """
    cities = INDIA_CITIES[:3] if dry_run else INDIA_CITIES

    seen_ids:     set[str]  = set()
    all_stations: list[dict] = []

    for i, city in enumerate(cities, 1):
        log.info("[%d/%d] Google Places: fetching near %s ...",
                 i, len(cities), city["name"])
        try:
            places    = _fetch_city(city["lat"], city["lng"])
            new_count = 0

            for place in places:
                place_id = f"gp-{place.get('id', '')}"
                if place_id in seen_ids:
                    continue
                seen_ids.add(place_id)

                station = _parse_place(place)
                if station:
                    all_stations.append(station)
                    new_count += 1

            log.info("  → %d places returned, %d new stations",
                     len(places), new_count)

        except Exception as exc:
            log.error("  ✗ Google Places failed for %s: %s",
                      city["name"], exc)

        if i < len(cities):
            time.sleep(REQUEST_DELAY_SECONDS)

    log.info("Google Places total: %d stations fetched", len(all_stations))
    return all_stations
