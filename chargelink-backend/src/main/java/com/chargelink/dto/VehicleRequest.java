package com.chargelink.dto;

import com.chargelink.entity.enums.ConnectorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleRequest {
    @NotBlank(message = "Registration number is required")
    private String registrationNo;

    @NotBlank(message = "Brand is required")
    private String brand;

    @NotBlank(message = "Model is required")
    private String model;

    @NotNull(message = "Connector type is required (e.g. CCS2, Type2)")
    private ConnectorType connectorType;

    private Integer batteryCapacityKwh;
    private Integer rangeKm;
}
