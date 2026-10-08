package com.auction.domain.auction;

import java.time.Instant;
import java.util.UUID;

public record Bid(UUID bidId, UUID bidderId, Money amount, Instant placedAt) {
}
