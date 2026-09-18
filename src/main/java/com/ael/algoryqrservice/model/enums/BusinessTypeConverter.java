package com.ael.algoryqrservice.model.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BusinessTypeConverter implements AttributeConverter<BusinessType, String> {

    @Override
    public String convertToDatabaseColumn(BusinessType attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public BusinessType convertToEntityAttribute(String dbData) {
        return dbData == null || dbData.isBlank() ? null : BusinessType.fromCode(dbData);
    }
}
