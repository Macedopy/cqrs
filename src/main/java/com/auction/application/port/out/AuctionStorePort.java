package com.auction.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Driven port: persistence of the write model. Implemented in infrastructure. */
public interface AuctionStorePort {

    Optional<AuctionRecord> findById(UUID id);

    AuctionRecord save(AuctionRecord auction);
}