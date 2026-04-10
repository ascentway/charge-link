package com.chargelink.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Data
public class CreateBookingRequest {
    @NotNull(message = "Charger ID is required")
    private UUID chargerId;

    private UUID vehicleId;

    @NotNull(message = "Slot start time is required")
    private ZonedDateTime slotStart;

    @NotNull(message = "Slot end time is required")
    private ZonedDateTime slotEnd;

    private BigDecimal estimatedKwh;
    private String notes;
}
