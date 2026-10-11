package com.auction.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AuctionSummaryResponse(
        UUID auctionId,
        String title,
        String category,
        String status,
        BigDecimal currentPrice,
        String currency,
        UUID highestBidderId,
        int bidCount,
        Instant endsAt,
        long lastEventVersion,
        Instant updatedAt) {
}