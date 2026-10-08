package com.auction.domain.auction.commands;

import java.time.Instant;
import java.util.UUID;

public record CreateAuctionCommand(
        UUID sellerId,
        String title,
        String description,
        String category,
        int lotQuantity,
        String lotUnit,
        String startingPrice,
        String currency,
        String minimumIncrement,
        String reservePrice,
        String buyNowPrice,
        Instant startsAt,
        Instant endsAt,
        long antiSnipingSeconds) {
}
