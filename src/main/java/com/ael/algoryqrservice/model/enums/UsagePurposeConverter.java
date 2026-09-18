package com.ael.algoryqrservice.model.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UsagePurposeConverter implements AttributeConverter<UsagePurpose, String> {

    @Override
    public String convertToDatabaseColumn(UsagePurpose attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public UsagePurpose convertToEntityAttribute(String dbData) {
        return dbData == null || dbData.isBlank() ? null : UsagePurpose.fromCode(dbData);
    }
}
