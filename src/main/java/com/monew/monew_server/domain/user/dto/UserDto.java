package com.monew.monew_server.domain.user.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class UserDto {

	private UUID id;
	private String email;
	private String nickname;
	private OffsetDateTime createdAt;

}
