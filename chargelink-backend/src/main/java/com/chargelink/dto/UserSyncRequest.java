package com.chargelink.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserSyncRequest {

    @NotBlank(message = "Full name must not be blank")
    private String fullName;

    // Optional: 10-digit Indian mobile number stored as BIGINT (e.g. 9876543210)
    @Min(value = 1000000000L, message = "Phone must be a valid 10-digit number")
    @Max(value = 9999999999L, message = "Phone must be a valid 10-digit number")
    private Long phone;

    private String authProvider; // e.g., 'email', 'google', 'phone'
}
