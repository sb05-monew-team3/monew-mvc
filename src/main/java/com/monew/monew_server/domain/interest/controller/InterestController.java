package com.monew.monew_server.domain.interest.controller;

import com.monew.monew_server.domain.interest.dto.CursorPageResponseInterestDto;
import com.monew.monew_server.domain.interest.dto.InterestDto;
import com.monew.monew_server.domain.interest.dto.InterestQuery;
import com.monew.monew_server.domain.interest.dto.InterestRegisterRequest;
import com.monew.monew_server.domain.interest.dto.InterestUpdateRequest;
import com.monew.monew_server.domain.interest.dto.SubscriptionDto;
import com.monew.monew_server.domain.interest.service.InterestService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/interests")
public class InterestController {

    private final InterestService interestService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public CursorPageResponseInterestDto findAll(
        @ModelAttribute InterestQuery query,
        @RequestHeader("Monew-Request-User-Id") UUID userId
    ) {
        return interestService.findAll(query, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterestDto create(@RequestBody @Valid InterestRegisterRequest request) {
        return interestService.create(request);
    }

    @DeleteMapping("{interestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID interestId) {
        interestService.delete(interestId);
    }

    @PatchMapping("{interestId}")
    @ResponseStatus(HttpStatus.OK)
    public InterestDto update(
        @PathVariable UUID interestId,
        @RequestBody @Valid InterestUpdateRequest request
    ) {
        return interestService.update(interestId, request);
    }

    @PostMapping("{interestId}/subscriptions")
    @ResponseStatus(HttpStatus.OK)
    public SubscriptionDto subscribe(
        @PathVariable UUID interestId,
        @RequestHeader("Monew-Request-User-Id") UUID userId
    ) {
        return interestService.subscribe(interestId, userId);
    }

    @DeleteMapping("{interestId}/subscriptions")
    @ResponseStatus(HttpStatus.OK)
    public void unsubscribe(
        @PathVariable UUID interestId,
        @RequestHeader("Monew-Request-User-Id") UUID userId
    ) {
        interestService.unsubscribe(interestId, userId);
    }
}
