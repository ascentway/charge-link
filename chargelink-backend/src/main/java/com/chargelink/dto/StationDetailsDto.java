package com.chargelink.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class StationDetailsDto {
    private UUID id;
    private String networkName;
    private String externalId;
    private String name;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private Double lat;
    private Double lng;
    private String[] amenities;
    private Map<String, String> operatingHours;
    private String dataSource;
    private Boolean isVerified;
    private List<ChargerDto> chargers;
}
