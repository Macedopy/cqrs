package com.auction.domain.auction.exceptions;

public class AuctionNotOpenException extends AuctionDomainException {

    public AuctionNotOpenException() {
        super("Auction is not open for bids");
    }
}
