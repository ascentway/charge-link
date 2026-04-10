package com.chargelink.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
public class SessionDto {
    private UUID id;
    private UUID bookingId;
    private UUID chargerId;
    private String chargerCode;
    private String stationName;
    private BigDecimal energyDeliveredKwh;
    private Integer durationMinutes;
    private BigDecimal amountCharged;
    private String currency;
    private String paymentStatus;
    private ZonedDateTime startedAt;
    private ZonedDateTime endedAt;
}
