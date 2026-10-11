package com.auction.application.query;

import com.auction.application.dto.AuctionSummaryResponse;
import com.auction.infrastructure.read.AuctionSummaryEntity;
import com.auction.infrastructure.read.AuctionSummaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Query side: reads only the read model, never the write tables.
 */
@Service
@Transactional(readOnly = true)
public class AuctionQueryService {

    private final AuctionSummaryRepository repository;

    public AuctionQueryService(AuctionSummaryRepository repository) {
        this.repository = repository;
    }

    public Optional<AuctionSummaryResponse> findSummary(UUID auctionId) {
        return repository.findById(auctionId).map(AuctionQueryService::toResponse);
    }

    public List<AuctionSummaryResponse> findHotAuctions(int limit) {
        return repository.findAllByOrderByBidCountDesc().stream()
                .limit(limit)
                .map(AuctionQueryService::toResponse)
                .toList();
    }

    public List<AuctionSummaryResponse> findByHighestBidder(UUID bidderId) {
        return repository.findByHighestBidderIdOrderByUpdatedAtDesc(bidderId).stream()
                .map(AuctionQueryService::toResponse)
                .toList();
    }

    private static AuctionSummaryResponse toResponse(AuctionSummaryEntity entity) {
        return new AuctionSummaryResponse(
                entity.getAuctionId(),
                entity.getTitle(),
                entity.getCategory(),
                entity.getStatus(),
                entity.getCurrentPrice(),
                entity.getCurrency(),
                entity.getHighestBidderId(),
                entity.getBidCount(),
                entity.getEndsAt(),
                entity.getLastEventVersion(),
                entity.getUpdatedAt());
    }
}