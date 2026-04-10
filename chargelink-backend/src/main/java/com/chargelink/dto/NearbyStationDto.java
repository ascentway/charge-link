package com.chargelink.dto;

import java.util.UUID;

/**
 * Projection interface mapping the results of the native stored procedure `find_nearby_stations`.
 */
public interface NearbyStationDto {
    UUID getStationId();
    String getStationName();
    String getAddress();
    String getCity();
    Double getDistanceKm();
    String getNetworkName();
    Long getAvailable();
    Long getTotal();
}
