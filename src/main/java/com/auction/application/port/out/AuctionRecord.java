package com.auction.application.port.out;

import com.auction.domain.auction.AuctionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Application-level representation of an auction.
 * Deliberately free of JPA and AWS types so the core never depends on adapters.
 */
public record AuctionRecord(
        UUID id,
        UUID sellerId,
        String title,
        String description,
        String category,
        int lotQuantity,
        String lotUnit,
        BigDecimal startingPrice,
        String currency,
        BigDecimal minimumIncrement,
        BigDecimal reservePrice,
        BigDecimal buyNowPrice,
        Instant startsAt,
        Instant endsAt,
        long antiSnipingSeconds,
        AuctionStatus status,
        BigDecimal currentPrice,
        UUID highestBidderId,
        long aggregateVersion) {
}