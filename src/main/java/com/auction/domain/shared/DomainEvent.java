package com.auction.domain.shared;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {

    UUID eventId();

    UUID aggregateId();

    long aggregateVersion();

    Instant occurredAt();

    default String eventType() {
        return getClass().getSimpleName();
    }
}
