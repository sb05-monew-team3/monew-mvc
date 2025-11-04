package com.monew.monew_server.domain.interest.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.monew.monew_server.domain.common.BaseEntity;
import com.monew.monew_server.domain.user.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "subscriptions",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_subscriptions_user_id_interest_id",
		columnNames = {"user_id", "interest_id"}
	))
@Getter
@Setter
@SuperBuilder
@ToString(callSuper = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "interest_id")
	private Interest interest;
}
