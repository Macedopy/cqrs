package com.auction.infrastructure.persistence;

import com.auction.domain.auction.AuctionStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auction")
public class AuctionEntity {

    @Id
    private UUID id;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private String category;

    @Column(name = "lot_quantity", nullable = false)
    private int lotQuantity;

    @Column(name = "lot_unit", nullable = false)
    private String lotUnit;

    @Column(name = "starting_price", nullable = false)
    private BigDecimal startingPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "minimum_increment", nullable = false)
    private BigDecimal minimumIncrement;

    @Column(name = "reserve_price")
    private BigDecimal reservePrice;

    @Column(name = "buy_now_price")
    private BigDecimal buyNowPrice;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "anti_sniping_seconds", nullable = false)
    private long antiSnipingSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuctionStatus status;

    @Column(name = "current_price")
    private BigDecimal currentPrice;

    @Column(name = "highest_bidder_id")
    private UUID highestBidderId;

    @Version
    private Long version;

    /**
     * Domain aggregate version: used to order and deduplicate events.
     * Kept separate from {@link #version}, which is only the JPA optimistic
     * locking counter and starts at 0 on insert.
     */
    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuctionEntity() {
    }

    public AuctionEntity(UUID id, UUID sellerId, String title, String description, String category,
                         int lotQuantity, String lotUnit, BigDecimal startingPrice, String currency,
                         BigDecimal minimumIncrement, BigDecimal reservePrice, BigDecimal buyNowPrice,
                         Instant startsAt, Instant endsAt, long antiSnipingSeconds, AuctionStatus status) {
        this.id = id;
        this.sellerId = sellerId;
        this.title = title;
        this.description = description;
        this.category = category;
        this.lotQuantity = lotQuantity;
        this.lotUnit = lotUnit;
        this.startingPrice = startingPrice;
        this.currency = currency;
        this.minimumIncrement = minimumIncrement;
        this.reservePrice = reservePrice;
        this.buyNowPrice = buyNowPrice;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.antiSnipingSeconds = antiSnipingSeconds;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getSellerId() { return sellerId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public int getLotQuantity() { return lotQuantity; }
    public String getLotUnit() { return lotUnit; }
    public BigDecimal getStartingPrice() { return startingPrice; }
    public String getCurrency() { return currency; }
    public BigDecimal getMinimumIncrement() { return minimumIncrement; }
    public BigDecimal getReservePrice() { return reservePrice; }
    public BigDecimal getBuyNowPrice() { return buyNowPrice; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public long getAntiSnipingSeconds() { return antiSnipingSeconds; }
    public AuctionStatus getStatus() { return status; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public UUID getHighestBidderId() { return highestBidderId; }
    public Long getVersion() { return version; }
    public long getAggregateVersion() { return aggregateVersion; }
    public void setAggregateVersion(long aggregateVersion) { this.aggregateVersion = aggregateVersion; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(AuctionStatus status) { this.status = status; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    public void setHighestBidderId(UUID highestBidderId) { this.highestBidderId = highestBidderId; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
}
