package com.chargelink.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
public class BookingDto {
    private UUID id;
    private UUID chargerId;
    private String chargerCode;
    private String stationName;
    private ZonedDateTime slotStart;
    private ZonedDateTime slotEnd;
    private String status;
    private BigDecimal estimatedKwh;
    private String notes;
}
