package com.auction.application.service;

import com.auction.application.AuctionNotFoundException;
import com.auction.application.port.in.AuctionCommandUseCase;
import com.auction.application.port.out.*;
import com.auction.domain.auction.Auction;
import com.auction.domain.auction.Bid;
import com.auction.domain.auction.Money;
import com.auction.domain.auction.commands.*;
import com.auction.domain.shared.DomainEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Use case orchestration. Depends only on driven ports, never on adapters.
 * Business rules live in the Auction aggregate, not here.
 */
@Service
public class AuctionApplicationService implements AuctionCommandUseCase {

    private final AuctionStorePort auctionStore;
    private final BidStorePort bidStore;
    private final OutboxStorePort outboxStore;
    private final ObjectMapper objectMapper;

    public AuctionApplicationService(AuctionStorePort auctionStore, BidStorePort bidStore,
                                     OutboxStorePort outboxStore, ObjectMapper objectMapper) {
        this.auctionStore = auctionStore;
        this.bidStore = bidStore;
        this.outboxStore = outboxStore;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public UUID createAuction(CreateAuctionCommand command) {
        UUID id = UUID.randomUUID();
        Auction auction = Auction.create(id, command, Instant.now());

        auctionStore.save(new AuctionRecord(
                id, command.sellerId(), command.title(), command.description(),
                command.category() != null ? command.category() : "SINGLE_ITEM",
                command.lotQuantity() > 0 ? command.lotQuantity() : 1,
                command.lotUnit() != null ? command.lotUnit() : "unit",
                new BigDecimal(command.startingPrice()), command.currency(),
                new BigDecimal(command.minimumIncrement()),
                command.reservePrice() != null ? new BigDecimal(command.reservePrice()) : null,
                command.buyNowPrice() != null ? new BigDecimal(command.buyNowPrice()) : null,
                command.startsAt(), command.endsAt(),
                command.antiSnipingSeconds() > 0 ? command.antiSnipingSeconds() : 120,
                auction.getStatus(), null, null, auction.getVersion()));

        appendEvents(auction);
        return id;
    }

    @Override
    @Transactional
    public void openAuction(OpenAuctionCommand command) {
        Auction auction = load(command.auctionId());
        auction.open(Instant.now());
        persist(auction, null);
    }

    @Override
    @Transactional
    public void placeBid(PlaceBidCommand command) {
        AuctionRecord record = require(command.auctionId());
        Auction auction = toAggregate(record);

        auction.placeBid(command.bidderId(),
                Money.of(command.amount(), command.currency()),
                Instant.now());

        List<Bid> bids = auction.getBids();
        Bid newBid = bids.isEmpty() ? null : bids.getLast();

        persist(auction, newBid);
    }

    @Override
    @Transactional
    public void closeAuction(CloseAuctionCommand command) {
        Auction auction = load(command.auctionId());
        auction.close(Instant.now());
        persist(auction, null);
    }

    @Override
    @Transactional
    public void cancelAuction(CancelAuctionCommand command) {
        Auction auction = load(command.auctionId());
        auction.cancel(Instant.now());
        persist(auction, null);
    }

    private Auction load(UUID id) {
        return toAggregate(require(id));
    }

    private AuctionRecord require(UUID id) {
        return auctionStore.findById(id)
                .orElseThrow(() -> new AuctionNotFoundException(id));
    }

    private void persist(Auction auction, Bid newBid) {
        AuctionRecord current = require(auction.getId());

        auctionStore.save(new AuctionRecord(
                current.id(), current.sellerId(), current.title(), current.description(),
                current.category(), current.lotQuantity(), current.lotUnit(),
                current.startingPrice(), current.currency(), current.minimumIncrement(),
                current.reservePrice(), current.buyNowPrice(), current.startsAt(),
                auction.getEndsAt(), current.antiSnipingSeconds(),
                auction.getStatus(),
                auction.getCurrentPrice() != null ? auction.getCurrentPrice().amount() : null,
                auction.getHighestBidderId(),
                auction.getVersion()));

        if (newBid != null) {
            bidStore.save(new BidRecord(newBid.bidId(), auction.getId(), newBid.bidderId(),
                    newBid.amount().amount(), newBid.amount().currency().getCurrencyCode(),
                    newBid.placedAt()));
        }

        appendEvents(auction);
    }

    private void appendEvents(Auction auction) {
        for (DomainEvent event : auction.getPendingEvents()) {
            outboxStore.append(new OutboxRecord(
                    event.eventId(), event.aggregateId(), "Auction",
                    event.eventType(), serialize(event), event.aggregateVersion(), event.occurredAt()));
        }
        auction.clearPendingEvents();
    }

    private String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize event", e);
        }
    }

    private Auction toAggregate(AuctionRecord record) {
        return Auction.reconstitute(
                record.id(), record.aggregateVersion(), record.sellerId(), record.title(),
                record.description(), record.category(), record.lotQuantity(), record.lotUnit(),
                Money.of(record.startingPrice().toPlainString(), record.currency()),
                Money.of(record.minimumIncrement().toPlainString(), record.currency()),
                record.reservePrice() != null
                        ? Money.of(record.reservePrice().toPlainString(), record.currency()) : null,
                record.buyNowPrice() != null
                        ? Money.of(record.buyNowPrice().toPlainString(), record.currency()) : null,
                record.startsAt(), record.endsAt(), record.antiSnipingSeconds(),
                record.status(),
                record.currentPrice() != null
                        ? Money.of(record.currentPrice().toPlainString(), record.currency()) : null,
                record.highestBidderId(), List.of());
    }
}