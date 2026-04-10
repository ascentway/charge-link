package com.chargelink.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReportStatusRequest {
    @NotBlank(message = "Reported status is required")
    private String reportedStatus;

    private String note;
    private String photoUrl;
}
