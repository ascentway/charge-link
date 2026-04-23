package com.chargelink.entity.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConnectorType {
    CCS2("CCS2"),
    CHADEMO("CHAdeMO"),
    TYPE2("Type2"),
    GB_T("GB/T"),
    BHARAT_AC("Bharat AC"),
    BHARAT_DC("Bharat DC");


    @JsonValue
    private final String value;

    public static ConnectorType fromString(String text) {
        for (ConnectorType b : ConnectorType.values()) {
            if (b.value.equalsIgnoreCase(text)) {
                return b;
            }
        }
        throw new IllegalArgumentException("No valid ConnectorType for value: " + text);
    }
}
