package com.monew.monew_server.domain.notification.entity;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.monew.monew_server.domain.common.BaseUpdatableEntity;
import com.monew.monew_server.domain.notification.converter.NotificationResourceTypeConverter;
import com.monew.monew_server.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@SuperBuilder
@ToString(callSuper = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseUpdatableEntity {

	@Builder.Default
	@Column(nullable = false)
	private boolean confirmed = false;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(columnDefinition = "text", nullable = false)
	private String content;

	@Convert(converter = NotificationResourceTypeConverter.class)
	@Column(nullable = false, length = 50)
	private NotificationResourceType resourceType;

	@Column(nullable = false)
	private UUID resourceId;

	public void confirm() {
		this.confirmed = true;
	}
}
