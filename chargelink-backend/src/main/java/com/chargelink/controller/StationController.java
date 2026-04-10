package com.chargelink.controller;

import com.chargelink.dto.NearbyStationDto;
import com.chargelink.dto.StationDetailsDto;
import com.chargelink.service.StationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stations")
@RequiredArgsConstructor
public class StationController {

    private final StationService stationService;

    /**
     * Find nearby stations using PostGIS matching radius and optional connector filter.
     * Maps to: GET /api/v1/stations/nearby
     */
    @GetMapping("/nearby")
    public ResponseEntity<List<NearbyStationDto>> getNearbyStations(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(required = false, defaultValue = "5.0") Double radiusKm,
            @RequestParam(required = false) String connectorFilter) {

        List<NearbyStationDto> stations = stationService.getNearbyStations(lat, lng, radiusKm, connectorFilter);
        return ResponseEntity.ok(stations);
    }

    /**
     * Retrieves full station details including all nested chargers.
     * Maps to: GET /api/v1/stations/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<StationDetailsDto> getStationDetails(@PathVariable UUID id) {
        StationDetailsDto details = stationService.getStationById(id);
        return ResponseEntity.ok(details);
    }
}
