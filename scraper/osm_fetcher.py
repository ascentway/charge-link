"""
osm_fetcher.py
Fetches EV charging stations from OpenStreetMap via Overpass API.

Completely free, no API key needed.
OSM has crowd-mapped stations that OCM and Google both miss —
especially smaller operators, residential complexes, and newer installs.
"""

import time
import logging

import requests
from tenacity import retry, stop_after_attempt, wait_exponential

log = logging.getLogger(__name__)

OVERPASS_URL = "https://overpass-api.de/api/interpreter"


@retry(stop=stop_after_attempt(3), wait=wait_exponential(min=3, max=20))
def _query_india() -> list[dict]:
    """
    Fetch all amenity=charging_station nodes in India bounding box.
    Bounding box: lat 6–37, lng 68–97.5 (covers all of India)
    """
    query = """
    [out:json][timeout:60];
    (
      node["amenity"="charging_station"]
        (6.0, 68.0, 37.0, 97.5);
      way["amenity"="charging_station"]
        (6.0, 68.0, 37.0, 97.5);
    );
    out center tags;
    """
    resp = requests.post(
        OVERPASS_URL,
        data={"data": query},
        timeout=90,
        headers={"User-Agent": "ChargeLinK-Scraper/1.0"},
    )
    resp.raise_for_status()
    return resp.json().get("elements", [])


# OSM socket type → ChargeLinK connector
OSM_SOCKET_MAP: dict[str, str] = {
    "type2":          "Type2",
    "type1":          "Type1",
    "ccs":            "CCS2",
    "ccs2":           "CCS2",
    "ccs1":           "CCS1",
    "chademo":        "CHAdeMO",
    "tesla":          "Tesla",
    "mennekes":       "Type2",
    "bharat_ac_001":  "Bharat AC",
    "bharat_dc_001":  "Bharat DC",
    "gbt_ac":         "GB/T AC",
    "gbt_dc":         "GB/T DC",
}

NETWORK_SLUG_MAP: dict[str, str] = {
    "tata":       "tata-power-ev",
    "chargezone": "chargezone",
    "statiq":     "statiq",
    "ather":      "ather-grid",
    "bpcl":       "bpcl-ev",
    "volttic":    "volttic",
    "evre":       "evre",
    "zeon":       "zeon-charging",
}


def _parse_osm_element(el: dict) -> dict | None:
    """Parse an OSM element into our DB shape."""
    try:
        tags = el.get("tags") or {}

        # Get coordinates (nodes have direct lat/lng, ways have center)
        if el["type"] == "node":
            lat = el.get("lat")
            lng = el.get("lon")
        else:
            center = el.get("center") or {}
            lat = center.get("lat")
            lng = center.get("lon")

        if lat is None or lng is None:
            return None

        name    = tags.get("name") or tags.get("operator") or "EV Charging Station"
        address = tags.get("addr:full") or tags.get("addr:street") or ""
        city    = tags.get("addr:city") or tags.get("addr:town") or ""
        state   = tags.get("addr:state") or ""
        pincode = tags.get("addr:postcode") or ""
        network = tags.get("network") or tags.get("operator") or ""

        # Detect network slug
        network_slug = None
        for keyword, slug in NETWORK_SLUG_MAP.items():
            if keyword in network.lower():
                network_slug = slug
                break

        # Build charger list from socket tags
        # OSM uses socket:type2=2, socket:chademo=1 etc.
        chargers = []
        socket_tags = {k: v for k, v in tags.items() if k.startswith("socket:")}

        if socket_tags:
            for key, count_str in socket_tags.items():
                socket_type = key.replace("socket:", "").lower()
                connector   = OSM_SOCKET_MAP.get(socket_type, "Type2")
                try:
                    count = int(count_str)
                except ValueError:
                    count = 1

                for j in range(count):
                    chargers.append({
                        "charger_code":   f"OSM-{socket_type}-{j+1}",
                        "connector_type": connector,
                        "power_kw":       float(tags.get("socket:output", "0").replace("kW", "").strip() or 0),
                        "current_type":   "AC" if connector in ("Type1", "Type2", "Bharat AC") else "DC",
                        "current_status": "unknown",
                        "status_source":  "osm",
                        "is_active":      True,
                    })
        else:
            # No socket tags — create generic entry
            capacity = tags.get("capacity") or "1"
            try:
                count = int(capacity)
            except ValueError:
                count = 1
            for j in range(count):
                chargers.append({
                    "charger_code":   f"OSM-1-{j+1}",
                    "connector_type": "Type2",
                    "power_kw":       0.0,
                    "current_type":   "AC",
                    "current_status": "unknown",
                    "status_source":  "osm",
                    "is_active":      True,
                })

        if not chargers:
            return None

        return {
            "external_id":  f"osm-{el['id']}",
            "name":         name.strip(),
            "address":      address.strip(),
            "city":         city.strip(),
            "state":        state.strip(),
            "pincode":      pincode.strip(),
            "lat":          float(lat),
            "lng":          float(lng),
            "data_source":  "openstreetmap",
            "is_verified":  False,
            "network_slug": network_slug,
            "chargers":     chargers,
        }

    except Exception as exc:
        log.warning("Failed to parse OSM element %s: %s", el.get("id"), exc)
        return None


def fetch_osm_stations() -> list[dict]:
    """
    Fetch all EV charging stations in India from OpenStreetMap.
    No API key needed. Single call covers all of India.
    """
    log.info("Fetching from OpenStreetMap Overpass API (single India-wide query) ...")
    try:
        elements = _query_india()
        log.info("  → %d OSM elements returned", len(elements))
    except Exception as exc:
        log.error("Overpass API failed: %s", exc)
        return []

    stations  = []
    seen_ids: set[str] = set()

    for el in elements:
        ext_id = f"osm-{el['id']}"
        if ext_id in seen_ids:
            continue
        seen_ids.add(ext_id)

        station = _parse_osm_element(el)
        if station:
            stations.append(station)

    log.info("OSM total: %d stations parsed", len(stations))
    return stations
