package com.monew.monew_server.domain.interest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record InterestRegisterRequest(
    @NotNull
    @Size(min = 1, max = 50)
    String name,

    @NotNull
    @Size(min = 1, max = 10, message = "관련 키워드는 1개 이상 10개 이하로 작성해주세요")
    List<String> keywords
) {}
