package com.auction.domain.auction.events;

import com.auction.domain.shared.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record AuctionCreated(
        UUID eventId,
        UUID aggregateId,
        long aggregateVersion,
        Instant occurredAt,
        UUID sellerId,
        String title,
        String category,
        int lotQuantity,
        String lotUnit,
        String startingPrice,
        String currency,
        String minimumIncrement,
        String reservePrice,
        String buyNowPrice,
        Instant startsAt,
        Instant endsAt) implements DomainEvent {
}
