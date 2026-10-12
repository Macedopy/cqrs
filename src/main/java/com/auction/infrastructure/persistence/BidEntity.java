package com.auction.infrastructure.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bid")
public class BidEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false)
    private AuctionEntity auction;

    @Column(name = "bidder_id", nullable = false)
    private UUID bidderId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    public BidEntity() {
    }

    public BidEntity(UUID id, AuctionEntity auction, UUID bidderId, BigDecimal amount, String currency, Instant placedAt) {
        this.id = id;
        this.auction = auction;
        this.bidderId = bidderId;
        this.amount = amount;
        this.currency = currency;
        this.placedAt = placedAt;
    }

    public UUID getId() { return id; }
    public AuctionEntity getAuction() { return auction; }
    public void setId(UUID id) { this.id = id; }
    public void setAuction(AuctionEntity auction) { this.auction = auction; }
    public void setBidderId(UUID bidderId) { this.bidderId = bidderId; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setPlacedAt(Instant placedAt) { this.placedAt = placedAt; }
    public UUID getBidderId() { return bidderId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getPlacedAt() { return placedAt; }
}
