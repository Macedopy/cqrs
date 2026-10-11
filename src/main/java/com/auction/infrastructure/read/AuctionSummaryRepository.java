package com.auction.infrastructure.read;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuctionSummaryRepository extends JpaRepository<AuctionSummaryEntity, UUID> {

    List<AuctionSummaryEntity> findAllByOrderByBidCountDesc();

    List<AuctionSummaryEntity> findByHighestBidderIdOrderByUpdatedAtDesc(UUID highestBidderId);
}