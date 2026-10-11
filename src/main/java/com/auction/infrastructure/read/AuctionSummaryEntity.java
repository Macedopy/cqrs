package com.auction.infrastructure.read;

import com.auction.domain.auction.AuctionStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auction_summary")
public class AuctionSummaryEntity {

    @Id
    @Column(name = "auction_id")
    private UUID auctionId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "current_price")
    private BigDecimal currentPrice;

    @Column(name = "highest_bidder_id")
    private UUID highestBidderId;

    @Column(name = "bid_count", nullable = false)
    private int bidCount;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "last_event_version", nullable = false)
    private long lastEventVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AuctionSummaryEntity() {
    }

    public AuctionSummaryEntity(UUID auctionId, UUID sellerId, String title, String category, String status,
                                String currency, Instant endsAt, long lastEventVersion) {
        this.auctionId = auctionId;
        this.sellerId = sellerId;
        this.title = title;
        this.category = category;
        this.status = status;
        this.currency = currency;
        this.endsAt = endsAt;
        this.lastEventVersion = lastEventVersion;
        this.updatedAt = Instant.now();
    }

    public void applyBid(BigDecimal amount, UUID bidderId, long eventVersion) {
        if (eventVersion <= lastEventVersion) {
            return;
        }
        this.currentPrice = amount;
        this.highestBidderId = bidderId;
        this.bidCount++;
        this.lastEventVersion = eventVersion;
        this.updatedAt = Instant.now();
    }

    public void applyStatus(AuctionStatus newStatus, BigDecimal finalPrice, UUID winnerId, Instant newEndsAt,
                            long eventVersion) {
        if (eventVersion <= lastEventVersion) {
            return;
        }
        this.status = newStatus.name();
        if (finalPrice != null) {
            this.currentPrice = finalPrice;
        }
        if (winnerId != null) {
            this.highestBidderId = winnerId;
        }
        if (newEndsAt != null) {
            this.endsAt = newEndsAt;
        }
        this.lastEventVersion = eventVersion;
        this.updatedAt = Instant.now();
    }

    public UUID getAuctionId() { return auctionId; }
    public UUID getSellerId() { return sellerId; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getStatus() { return status; }
    public String getCurrency() { return currency; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public UUID getHighestBidderId() { return highestBidderId; }
    public int getBidCount() { return bidCount; }
    public Instant getEndsAt() { return endsAt; }
    public long getLastEventVersion() { return lastEventVersion; }
    public Instant getUpdatedAt() { return updatedAt; }
}