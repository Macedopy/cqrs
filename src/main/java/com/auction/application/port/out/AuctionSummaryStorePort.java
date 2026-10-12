package com.auction.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Driven port: the read side store. Implemented in infrastructure. */
public interface AuctionSummaryStorePort {

    Optional<AuctionSummaryRecord> findById(UUID auctionId);

    AuctionSummaryRecord save(AuctionSummaryRecord record);

    List<AuctionSummaryRecord> findTopByBidCount(int limit);

    List<AuctionSummaryRecord> findByHighestBidder(UUID bidderId);
}