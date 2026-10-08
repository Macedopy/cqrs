package com.auction.domain.auction.events;

import com.auction.domain.shared.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record BidPlaced(
        UUID eventId,
        UUID aggregateId,
        long aggregateVersion,
        Instant occurredAt,
        UUID bidderId,
        String amount,
        String currency) implements DomainEvent {
}
