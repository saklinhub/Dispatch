package com.dispatch.mapper;

import com.dispatch.dto.response.OutboxEventResponse;
import com.dispatch.entity.OutboxEvent;
import org.springframework.stereotype.Component;

@Component
public class OutboxMapper {

    public OutboxEventResponse toResponse(OutboxEvent event) {
        if (event == null) {
            return null;
        }
        return OutboxEventResponse.builder()
                .id(event.getId())
                .aggregateType(event.getAggregateType())
                .aggregateId(event.getAggregateId())
                .eventType(event.getEventType())
                .payload(event.getPayload())
                .status(event.getStatus())
                .errorMessage(event.getErrorMessage())
                .createdAt(event.getCreatedAt())
                .publishedAt(event.getPublishedAt())
                .build();
    }
}
