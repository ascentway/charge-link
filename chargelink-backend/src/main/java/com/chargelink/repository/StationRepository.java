package com.chargelink.repository;

import com.chargelink.dto.NearbyStationDto;
import com.chargelink.entity.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StationRepository extends JpaRepository<Station, UUID> {

    @Query(value = "SELECT * FROM public.find_nearby_stations(:lat, :lng, :radiusKm, CAST(CAST(:connectorFilter AS varchar) AS text))", nativeQuery = true)
    List<NearbyStationDto> findNearbyStations(
            @Param("lat") Double lat,
            @Param("lng") Double lng,
            @Param("radiusKm") Double radiusKm,
            @Param("connectorFilter") String connectorFilter
    );
}
