package com.chargelink.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class StartSessionRequest {
    @NotNull(message = "Charger ID is required")
    private UUID chargerId;

    private UUID bookingId; // Optional: Session might be started without a reservation
}
