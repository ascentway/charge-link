package com.chargelink.dto;

import com.chargelink.entity.enums.ConnectorType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class VehicleResponse {
    private UUID id;
    private String registrationNo;
    private String brand;
    private String model;
    private ConnectorType connectorType;
    private Integer batteryCapacityKwh;
    private Integer rangeKm;
}
