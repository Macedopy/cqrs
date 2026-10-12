package com.auction.infrastructure.persistence.adapter;

import com.auction.application.port.out.AuctionRecord;
import com.auction.application.port.out.AuctionStorePort;
import com.auction.domain.auction.AuctionStatus;
import com.auction.infrastructure.persistence.AuctionEntity;
import com.auction.infrastructure.persistence.AuctionRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter: maps between the application record and the JPA entity.
 * This is the only class that knows both worlds.
 */
@Repository
public class JpaAuctionStoreAdapter implements AuctionStorePort {

    private final AuctionRepository repository;

    public JpaAuctionStoreAdapter(AuctionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<AuctionRecord> findById(UUID id) {
        return repository.findById(id).map(JpaAuctionStoreAdapter::toRecord);
    }

    @Override
    public AuctionRecord save(AuctionRecord auction) {
        AuctionEntity entity = repository.findById(auction.id()).orElse(null);
        if (entity == null) {
            entity = new AuctionEntity();
        }
        entity.setId(auction.id());
        entity.setSellerId(auction.sellerId());
        entity.setTitle(auction.title());
        entity.setDescription(auction.description());
        entity.setCategory(auction.category());
        entity.setLotQuantity(auction.lotQuantity());
        entity.setLotUnit(auction.lotUnit());
        entity.setStartingPrice(auction.startingPrice());
        entity.setCurrency(auction.currency());
        entity.setMinimumIncrement(auction.minimumIncrement());
        entity.setReservePrice(auction.reservePrice());
        entity.setBuyNowPrice(auction.buyNowPrice());
        entity.setStartsAt(auction.startsAt());
        entity.setEndsAt(auction.endsAt());
        entity.setAntiSnipingSeconds(auction.antiSnipingSeconds());
        entity.setStatus(auction.status());
        entity.setCurrentPrice(auction.currentPrice());
        entity.setHighestBidderId(auction.highestBidderId());
        entity.setAggregateVersion(auction.aggregateVersion());
        return toRecord(repository.save(entity));
    }

    private static AuctionRecord toRecord(AuctionEntity entity) {
        return new AuctionRecord(
                entity.getId(),
                entity.getSellerId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getCategory(),
                entity.getLotQuantity(),
                entity.getLotUnit(),
                entity.getStartingPrice(),
                entity.getCurrency(),
                entity.getMinimumIncrement(),
                entity.getReservePrice(),
                entity.getBuyNowPrice(),
                entity.getStartsAt(),
                entity.getEndsAt(),
                entity.getAntiSnipingSeconds(),
                entity.getStatus() != null ? entity.getStatus() : AuctionStatus.DRAFT,
                entity.getCurrentPrice(),
                entity.getHighestBidderId(),
                entity.getAggregateVersion());
    }
}