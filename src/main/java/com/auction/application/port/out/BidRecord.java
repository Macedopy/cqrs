package com.auction.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Application-level representation of a bid. */
public record BidRecord(
        UUID id,
        UUID auctionId,
        UUID bidderId,
        BigDecimal amount,
        String currency,
        Instant placedAt) {
}