package com.chargelink.entity.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ConnectorTypeConverter implements AttributeConverter<ConnectorType, String> {

    @Override
    public String convertToDatabaseColumn(ConnectorType attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getValue();
    }

    @Override
    public ConnectorType convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return ConnectorType.fromString(dbData);
    }
}
