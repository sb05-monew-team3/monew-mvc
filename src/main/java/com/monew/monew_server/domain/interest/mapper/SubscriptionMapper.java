package com.monew.monew_server.domain.interest.mapper;

import com.monew.monew_server.domain.interest.dto.SubscriptionDto;
import com.monew.monew_server.domain.interest.entity.Subscription;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    @Mapping(source = "subscription.interest.id", target = "interestId")
    @Mapping(source = "subscription.interest.name", target = "interestName")
    @Mapping(source = "keywords", target = "interestKeywords")
    @Mapping(source = "subscriberCount", target = "interestSubscriberCount")
    SubscriptionDto toDto(Subscription subscription, List<String> keywords, Long subscriberCount);
}
