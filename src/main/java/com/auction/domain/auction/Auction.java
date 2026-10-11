package com.auction.domain.auction;

import com.auction.domain.auction.commands.CreateAuctionCommand;
import com.auction.domain.auction.events.*;
import com.auction.domain.auction.exceptions.AuctionNotOpenException;
import com.auction.domain.auction.exceptions.InvalidBidException;
import com.auction.domain.auction.exceptions.InvalidStateTransitionException;
import com.auction.domain.shared.AggregateRoot;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Auction extends AggregateRoot {

    private final UUID sellerId;
    private final String title;
    private final String description;
    private final String category;
    private final int lotQuantity;
    private final String lotUnit;
    private final Money startingPrice;
    private final Money minimumIncrement;
    private final Money reservePrice;
    private final Money buyNowPrice;
    private final Instant startsAt;
    private Instant endsAt;
    private final long antiSnipingSeconds;

    private AuctionStatus status;
    private Money currentPrice;
    private UUID highestBidderId;
    private final List<Bid> bids = new ArrayList<>();

    private Auction(CreateAuctionCommand command, UUID id, Instant now) {
        super(id, 0);
        this.sellerId = command.sellerId();
        this.title = requireText(command.title(), "Title is required");
        this.description = command.description();
        this.category = command.category() != null ? command.category() : AuctionCategory.SINGLE_ITEM;
        this.lotQuantity = command.lotQuantity() > 0 ? command.lotQuantity() : 1;
        this.lotUnit = command.lotUnit() != null ? command.lotUnit() : "unit";
        this.startingPrice = Money.of(command.startingPrice(), command.currency());
        this.minimumIncrement = Money.of(command.minimumIncrement(), command.currency());
        this.reservePrice = command.reservePrice() != null ? Money.of(command.reservePrice(), command.currency()) : null;
        this.buyNowPrice = command.buyNowPrice() != null ? Money.of(command.buyNowPrice(), command.currency()) : null;
        if (this.buyNowPrice != null && !this.buyNowPrice.isGreaterThan(this.startingPrice)) {
            throw new InvalidBidException("Buy now price must be greater than the starting price");
        }
        if (this.reservePrice != null && !this.reservePrice.isGreaterThanOrEqual(this.startingPrice)) {
            throw new InvalidBidException("Reserve price must be greater than or equal to the starting price");
        }
        this.startsAt = command.startsAt();
        this.endsAt = command.endsAt();
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new InvalidStateTransitionException("Auction must have a valid start and end time");
        }
        this.antiSnipingSeconds = command.antiSnipingSeconds() > 0 ? command.antiSnipingSeconds() : 120;
        this.status = AuctionStatus.DRAFT;
        this.currentPrice = null;
        this.highestBidderId = null;

        registerEvent(new AuctionCreated(
                UUID.randomUUID(), id, 1, now, sellerId, title, category, lotQuantity, lotUnit,
                startingPrice.amount().toPlainString(), startingPrice.currency().getCurrencyCode(),
                minimumIncrement.amount().toPlainString(),
                reservePrice != null ? reservePrice.amount().toPlainString() : null,
                buyNowPrice != null ? buyNowPrice.amount().toPlainString() : null,
                startsAt, endsAt));
    }

    public static Auction create(UUID id, CreateAuctionCommand command, Instant now) {
        return new Auction(command, id, now);
    }

    private Auction(UUID id, long version, UUID sellerId, String title, String description, String category,
                    int lotQuantity, String lotUnit, Money startingPrice, Money minimumIncrement, Money reservePrice,
                    Money buyNowPrice, Instant startsAt, Instant endsAt, long antiSnipingSeconds,
                    AuctionStatus status, Money currentPrice, UUID highestBidderId, List<Bid> bids) {
        super(id, version);
        this.sellerId = sellerId;
        this.title = title;
        this.description = description;
        this.category = category;
        this.lotQuantity = lotQuantity;
        this.lotUnit = lotUnit;
        this.startingPrice = startingPrice;
        this.minimumIncrement = minimumIncrement;
        this.reservePrice = reservePrice;
        this.buyNowPrice = buyNowPrice;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.antiSnipingSeconds = antiSnipingSeconds;
        this.status = status;
        this.currentPrice = currentPrice;
        this.highestBidderId = highestBidderId;
        this.bids.addAll(bids);
    }

    /**
     * Rebuilds the aggregate from persisted state without emitting any event.
     */
    public static Auction reconstitute(UUID id, long version, UUID sellerId, String title, String description,
                                       String category, int lotQuantity, String lotUnit, Money startingPrice,
                                       Money minimumIncrement, Money reservePrice, Money buyNowPrice,
                                       Instant startsAt, Instant endsAt, long antiSnipingSeconds,
                                       AuctionStatus status, Money currentPrice, UUID highestBidderId, List<Bid> bids) {
        return new Auction(id, version, sellerId, title, description, category, lotQuantity, lotUnit,
                startingPrice, minimumIncrement, reservePrice, buyNowPrice, startsAt, endsAt, antiSnipingSeconds,
                status, currentPrice, highestBidderId, bids == null ? List.of() : bids);
    }

    public void open(Instant now) {
        if (status != AuctionStatus.DRAFT) {
            throw new InvalidStateTransitionException("Only draft auctions can be opened");
        }
        if (now.isBefore(startsAt)) {
            throw new InvalidStateTransitionException("Auction cannot be opened before its start time");
        }
        if (!now.isBefore(endsAt)) {
            throw new InvalidStateTransitionException("Auction has already ended");
        }
        status = AuctionStatus.OPEN;
        registerEvent(new AuctionOpened(UUID.randomUUID(), getId(), nextVersion(), now));
    }

    public void placeBid(UUID bidderId, Money amount, Instant now) {
        if (status != AuctionStatus.OPEN) {
            throw new AuctionNotOpenException();
        }
        if (bidderId.equals(sellerId)) {
            throw new InvalidBidException("The seller cannot bid on their own auction");
        }
        if (!now.isBefore(endsAt)) {
            throw new InvalidBidException("The auction has already ended");
        }
        Money minimum = bids.isEmpty() ? startingPrice : currentPrice.add(minimumIncrement);
        if (!amount.isGreaterThanOrEqual(minimum)) {
            throw new InvalidBidException("Bid must be at least " + minimum.amount().toPlainString() + " " + minimum.currency());
        }

        Bid bid = new Bid(UUID.randomUUID(), bidderId, amount, now);
        bids.add(bid);
        currentPrice = amount;
        highestBidderId = bidderId;
        registerEvent(new BidPlaced(UUID.randomUUID(), getId(), nextVersion(), now,
                bidderId, amount.amount().toPlainString(), amount.currency().getCurrencyCode()));

        if (buyNowPrice != null && amount.isGreaterThanOrEqual(buyNowPrice)) {
            status = AuctionStatus.SOLD;
            registerEvent(new AuctionClosed(UUID.randomUUID(), getId(), nextVersion(), now, true,
                    highestBidderId, buyNowPrice.amount().toPlainString(),
                    buyNowPrice.currency().getCurrencyCode(), "BUY_NOW"));
            return;
        }

        if (Duration.between(now, endsAt).toSeconds() <= antiSnipingSeconds) {
            endsAt = endsAt.plusSeconds(antiSnipingSeconds);
            registerEvent(new AuctionExtended(UUID.randomUUID(), getId(), nextVersion(), now, endsAt));
        }
    }

    public void close(Instant now) {
        if (status != AuctionStatus.OPEN) {
            throw new InvalidStateTransitionException("Only open auctions can be closed");
        }
        boolean winner = highestBidderId != null && currentPrice != null
                && (reservePrice == null || currentPrice.isGreaterThanOrEqual(reservePrice));
        if (winner) {
            status = AuctionStatus.SOLD;
            registerEvent(new AuctionClosed(UUID.randomUUID(), getId(), nextVersion(), now, true,
                    highestBidderId, currentPrice.amount().toPlainString(),
                    currentPrice.currency().getCurrencyCode(), "CLOSED"));
        } else {
            status = AuctionStatus.UNSOLD;
            registerEvent(new AuctionClosed(UUID.randomUUID(), getId(), nextVersion(), now, false,
                    null, currentPrice != null ? currentPrice.amount().toPlainString() : null,
                    currentPrice != null ? currentPrice.currency().getCurrencyCode() : null,
                    highestBidderId == null ? "NO_BIDS" : "RESERVE_NOT_MET"));
        }
    }

    public void cancel(Instant now) {
        if (status != AuctionStatus.DRAFT && status != AuctionStatus.OPEN) {
            throw new InvalidStateTransitionException("Only draft or open auctions can be cancelled");
        }
        status = AuctionStatus.CANCELLED;
        registerEvent(new AuctionCancelled(UUID.randomUUID(), getId(), nextVersion(), now));
    }

    private long nextVersion() {
        return getVersion() + 1;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidBidException(message);
        }
        return value;
    }

    public AuctionStatus getStatus() { return status; }
    public Money getCurrentPrice() { return currentPrice; }
    public UUID getHighestBidderId() { return highestBidderId; }
    public UUID getSellerId() { return sellerId; }
    public String getTitle() { return title; }
    public int getLotQuantity() { return lotQuantity; }
    public String getLotUnit() { return lotUnit; }
    public Instant getEndsAt() { return endsAt; }
    public List<Bid> getBids() { return List.copyOf(bids); }
}
