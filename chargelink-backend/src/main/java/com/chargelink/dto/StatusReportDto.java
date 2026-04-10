package com.chargelink.dto;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
public class StatusReportDto {
    private UUID id;
    private UUID chargerId;
    private String reportedByFullName; // For UI display
    private String reportedStatus;
    private String note;
    private String photoUrl;
    private Integer confidence;
    private Boolean isApplied;
    private ZonedDateTime reportedAt;
}
