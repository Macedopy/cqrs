package com.auction.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Write-side view of an auction, safe to expose over HTTP. */
public record AuctionDetailsResponse(
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
        String status,
        BigDecimal currentPrice,
        UUID highestBidderId,
        long aggregateVersion) {
}