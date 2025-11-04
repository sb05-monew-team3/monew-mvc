package com.monew.monew_server.domain.common;

import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseDeletableEntity extends BaseUpdatableEntity {

	private Instant deletedAt;

	public void softDelete() {
		this.deletedAt = Instant.now();
	}

	public boolean isDeleted() {
		return this.deletedAt != null;
	}
}