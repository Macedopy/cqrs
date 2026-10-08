package com.auction.domain.auction.events;

import com.auction.domain.shared.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record AuctionClosed(
        UUID eventId,
        UUID aggregateId,
        long aggregateVersion,
        Instant occurredAt,
        boolean sold,
        UUID winningBidderId,
        String finalPrice,
        String currency,
        String reason) implements DomainEvent {
}
