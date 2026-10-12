package com.auction.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Application-level representation of an outbox entry. */
public record OutboxRecord(
        UUID id,
        UUID aggregateId,
        String aggregateType,
        String eventType,
        String payload,
        long aggregateVersion,
        Instant occurredAt) {
}