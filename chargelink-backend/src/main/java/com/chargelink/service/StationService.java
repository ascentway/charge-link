package com.chargelink.service;

import com.chargelink.dto.NearbyStationDto;
import com.chargelink.dto.StationDetailsDto;
import com.chargelink.dto.StationMapper;
import com.chargelink.entity.Station;
import com.chargelink.exception.ResourceNotFoundException;
import com.chargelink.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StationService {

    private final StationRepository stationRepository;
    private final StationMapper stationMapper;

    /**
     * Finds nearby stations using the PostGIS function.
     */
    @Transactional(readOnly = true)
    public List<NearbyStationDto> getNearbyStations(Double lat, Double lng, Double radiusKm, String connectorFilter) {
        log.info("Searching for stations near lat={}, lng={}, radius={}km, connectorFilter={}", lat, lng, radiusKm, connectorFilter);
        // Fallback defaults if nulls are passed in request
        if (radiusKm == null) {
            radiusKm = 5.0; // Default 5km radius
        }
        return stationRepository.findNearbyStations(lat, lng, radiusKm, connectorFilter);
    }

    /**
     * Gets full station details including chargers.
     */
    @Transactional(readOnly = true)
    public StationDetailsDto getStationById(UUID id) {
        log.info("Fetching details for station {}", id);

        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found with id: " + id));

        return stationMapper.toStationDetailsDto(station);
    }
}
