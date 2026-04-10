package com.chargelink.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StopSessionRequest {
    private BigDecimal energyDeliveredKwh;
    private Integer durationMinutes;
    private BigDecimal amountCharged;
}
