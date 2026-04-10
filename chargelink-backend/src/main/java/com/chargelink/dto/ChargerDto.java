package com.chargelink.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
public class ChargerDto {
    private UUID id;
    private String chargerCode;
    private String connectorType;
    private BigDecimal powerKw;
    private String currentType;
    private String currentStatus;
    private ZonedDateTime statusUpdatedAt;
    private String statusSource;
    private BigDecimal pricePerKwh;
    private BigDecimal pricePerMin;
    private Boolean isActive;
}
