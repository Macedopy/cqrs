package com.auction.infrastructure.read.adapter;

import com.auction.application.port.out.AuctionSummaryRecord;
import com.auction.application.port.out.AuctionSummaryStorePort;
import com.auction.infrastructure.read.AuctionSummaryEntity;
import com.auction.infrastructure.read.AuctionSummaryRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaAuctionSummaryStoreAdapter implements AuctionSummaryStorePort {

    private final AuctionSummaryRepository repository;

    public JpaAuctionSummaryStoreAdapter(AuctionSummaryRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<AuctionSummaryRecord> findById(UUID auctionId) {
        return repository.findById(auctionId).map(JpaAuctionSummaryStoreAdapter::toRecord);
    }

    @Override
    public AuctionSummaryRecord save(AuctionSummaryRecord record) {
        AuctionSummaryEntity entity = repository.findById(record.auctionId()).orElse(null);
        if (entity == null) {
            entity = new AuctionSummaryEntity();
            entity.setAuctionId(record.auctionId());
            entity.setSellerId(record.sellerId());
            entity.setTitle(record.title());
            entity.setCategory(record.category());
        }
        entity.setStatus(record.status());
        entity.setCurrency(record.currency());
        entity.setCurrentPrice(record.currentPrice());
        entity.setHighestBidderId(record.highestBidderId());
        entity.setBidCount(record.bidCount());
        entity.setEndsAt(record.endsAt());
        entity.setLastEventVersion(record.lastEventVersion());
        entity.setUpdatedAt(record.updatedAt());
        return toRecord(repository.save(entity));
    }

    @Override
    public List<AuctionSummaryRecord> findTopByBidCount(int limit) {
        return repository.findAllByOrderByBidCountDesc().stream()
                .limit(limit)
                .map(JpaAuctionSummaryStoreAdapter::toRecord)
                .toList();
    }

    @Override
    public List<AuctionSummaryRecord> findByHighestBidder(UUID bidderId) {
        return repository.findByHighestBidderIdOrderByUpdatedAtDesc(bidderId).stream()
                .map(JpaAuctionSummaryStoreAdapter::toRecord)
                .toList();
    }

    private static AuctionSummaryRecord toRecord(AuctionSummaryEntity entity) {
        return new AuctionSummaryRecord(
                entity.getAuctionId(),
                entity.getSellerId(),
                entity.getTitle(),
                entity.getCategory(),
                entity.getStatus(),
                entity.getCurrency(),
                entity.getCurrentPrice(),
                entity.getHighestBidderId(),
                entity.getBidCount(),
                entity.getEndsAt(),
                entity.getLastEventVersion(),
                entity.getUpdatedAt());
    }
}