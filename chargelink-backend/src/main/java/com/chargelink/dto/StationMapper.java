package com.chargelink.dto;

import com.chargelink.entity.Charger;
import com.chargelink.entity.Station;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface StationMapper {

    @Mapping(target = "networkName", source = "network.name")
    StationDetailsDto toStationDetailsDto(Station station);

    ChargerDto toChargerDto(Charger charger);
}
