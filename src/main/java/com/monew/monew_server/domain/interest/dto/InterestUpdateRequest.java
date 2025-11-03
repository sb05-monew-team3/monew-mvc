package com.monew.monew_server.domain.interest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record InterestUpdateRequest(
    @NotNull
    @Size(min = 1, max = 10)
    List<String> keywords
) {}
