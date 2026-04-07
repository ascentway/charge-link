package com.chargelink.dto;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
public class UserSyncResponse {
    private UUID id;
    private String fullName;
    private String email;
    private Long phone;
    private String authProvider;
    private ZonedDateTime createdAt;
}
