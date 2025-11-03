package com.monew.monew_server.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class UserLoginRequest {

	@NotBlank(message = "이메일을 입력해 주세요.")
	@Email(message = "이메일 또는 비밀번호가 올바르지 않습니다.")
	private String email;

	@NotBlank(message = "비밀번호를 입력해 주세요.")
	@Size(min = 6)
	@Pattern(
		regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).+$",
		message = "영문과 숫자, 특수문자를 포함해 6자 이상 입력해 주세요."
	)
	private String password;
}
