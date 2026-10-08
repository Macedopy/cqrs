package com.auction.domain.auction.commands;

import java.util.UUID;

public record PlaceBidCommand(UUID auctionId, UUID bidderId, String amount, String currency) {
}
