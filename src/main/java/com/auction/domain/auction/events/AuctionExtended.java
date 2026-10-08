package com.auction.domain.auction.events;

import com.auction.domain.shared.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record AuctionExtended(
        UUID eventId,
        UUID aggregateId,
        long aggregateVersion,
        Instant occurredAt,
        Instant newEndsAt) implements DomainEvent {
}
