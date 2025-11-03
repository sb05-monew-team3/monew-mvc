package com.monew.monew_server.domain.notification.converter;

import com.monew.monew_server.domain.notification.entity.NotificationResourceType;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NotificationResourceTypeConverter implements AttributeConverter<NotificationResourceType, String> {

	@Override
	public String convertToDatabaseColumn(NotificationResourceType attribute) {
		return attribute.getValue();
	}

	@Override
	public NotificationResourceType convertToEntityAttribute(String dbData) {
		return NotificationResourceType.valueOf(dbData.toUpperCase());
	}
}