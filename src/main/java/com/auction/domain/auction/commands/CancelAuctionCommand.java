package com.auction.domain.auction.commands;

import java.util.UUID;

public record CancelAuctionCommand(UUID auctionId) {
}
