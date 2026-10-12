package com.auction.application.port.out;

import java.util.UUID;

/** Driven port: persistence of bids. Implemented in infrastructure. */
public interface BidStorePort {

    void save(BidRecord bid);
}