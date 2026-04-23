package com.chargelink.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistDto {
    private UUID id;
    private UUID chargerId;
    private String chargerCode;
    private String stationName;
    private UUID vehicleId;
    private ZonedDateTime wantedFrom;
    private ZonedDateTime wantedTo;
    private String status;
    private ZonedDateTime notifiedAt;
    private ZonedDateTime joinedAt;
}
