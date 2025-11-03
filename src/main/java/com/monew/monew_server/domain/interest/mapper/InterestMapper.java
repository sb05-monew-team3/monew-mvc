package com.monew.monew_server.domain.interest.mapper;

import com.monew.monew_server.domain.interest.dto.InterestDto;
import com.monew.monew_server.domain.interest.entity.Interest;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InterestMapper {

    InterestDto toDto(Interest interest, List<String> keywords, Long subscriberCount, Boolean subscribedByMe);
}
