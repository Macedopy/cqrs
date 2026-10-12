package com.auction.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Application-level representation of the AuctionSummary read model. */
public record AuctionSummaryRecord(
        UUID auctionId,
        UUID sellerId,
        String title,
        String category,
        String status,
        String currency,
        BigDecimal currentPrice,
        UUID highestBidderId,
        int bidCount,
        Instant endsAt,
        long lastEventVersion,
        Instant updatedAt) {
}