package com.auction.application.port.in;

import com.auction.application.dto.AuctionSummaryResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Driving port: everything the outside world can ask the read side. */
public interface AuctionQueryUseCase {

    Optional<AuctionSummaryResponse> findSummary(UUID auctionId);

    List<AuctionSummaryResponse> findHotAuctions(int limit);

    List<AuctionSummaryResponse> findByHighestBidder(UUID bidderId);
}