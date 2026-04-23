package com.chargelink.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
public class JoinWaitlistRequest {
    @NotNull(message = "Charger ID is required")
    private UUID chargerId;

    @NotNull(message = "Vehicle ID is required")
    private UUID vehicleId;

    @NotNull(message = "Wanted from time is required")
    private ZonedDateTime wantedFrom;

    @NotNull(message = "Wanted to time is required")
    private ZonedDateTime wantedTo;
}
