package com.monew.monew_server.domain.interest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record InterestUpdateRequest(

    @NotNull
    @Size(min = 1, max = 10, message = "수정 키워드는 1개 이상 10개 이하로 작성해주세요")
    List<String> keywords
) {}
