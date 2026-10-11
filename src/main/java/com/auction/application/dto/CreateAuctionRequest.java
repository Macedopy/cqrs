package com.auction.application.dto;

import java.time.Instant;

public record CreateAuctionRequest(
        String sellerId,
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
