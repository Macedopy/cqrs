package com.auction.domain.auction.exceptions;

public class InvalidStateTransitionException extends AuctionDomainException {

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
