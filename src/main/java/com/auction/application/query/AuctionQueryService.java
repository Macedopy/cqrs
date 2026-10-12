package com.auction.application.query;

import com.auction.application.dto.AuctionSummaryResponse;
import com.auction.application.port.in.AuctionQueryUseCase;
import com.auction.application.port.out.AuctionSummaryRecord;
import com.auction.application.port.out.AuctionSummaryStorePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Query side. Reads the read model through the port, never the write tables.
 */
@Service
@Transactional(readOnly = true)
public class AuctionQueryService implements AuctionQueryUseCase {

    private final AuctionSummaryStorePort store;

    public AuctionQueryService(AuctionSummaryStorePort store) {
        this.store = store;
    }

    @Override
    public Optional<AuctionSummaryResponse> findSummary(UUID auctionId) {
        return store.findById(auctionId).map(AuctionQueryService::toResponse);
    }

    @Override
    public List<AuctionSummaryResponse> findHotAuctions(int limit) {
        return store.findTopByBidCount(limit).stream().map(AuctionQueryService::toResponse).toList();
    }

    @Override
    public List<AuctionSummaryResponse> findByHighestBidder(UUID bidderId) {
        return store.findByHighestBidder(bidderId).stream().map(AuctionQueryService::toResponse).toList();
    }

    private static AuctionSummaryResponse toResponse(AuctionSummaryRecord record) {
        return new AuctionSummaryResponse(
                record.auctionId(),
                record.title(),
                record.category(),
                record.status(),
                record.currentPrice(),
                record.currency(),
                record.highestBidderId(),
                record.bidCount(),
                record.endsAt(),
                record.lastEventVersion(),
                record.updatedAt());
    }
}