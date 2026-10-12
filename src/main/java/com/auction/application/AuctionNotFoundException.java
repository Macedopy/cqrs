package com.auction.application;

import java.util.UUID;

/** Raised when an auction id does not exist. */
public class AuctionNotFoundException extends RuntimeException {

    public AuctionNotFoundException(UUID id) {
        super("Auction not found: " + id);
    }
}