package com.auction.infrastructure.messaging;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * An auction event as received from SQS.
 */
public record DomainEventMessage(
        UUID eventId,
        UUID aggregateId,
        long aggregateVersion,
        Instant occurredAt,
        String eventType,
        JsonNode payload) {

    public static DomainEventMessage parse(String body, String eventType, ObjectMapper objectMapper) {
        JsonNode event = objectMapper.readTree(body);
        return new DomainEventMessage(
                UUID.fromString(event.path("eventId").asString()),
                UUID.fromString(event.path("aggregateId").asString()),
                event.path("aggregateVersion").asLong(),
                Instant.parse(event.path("occurredAt").asString()),
                eventType != null ? eventType : "Unknown",
                event);
    }
}