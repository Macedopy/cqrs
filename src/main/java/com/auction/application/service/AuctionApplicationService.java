package com.auction.application.service;

import com.auction.domain.auction.Auction;
import com.auction.domain.auction.Bid;
import com.auction.domain.auction.Money;
import com.auction.domain.auction.commands.*;
import com.auction.domain.shared.DomainEvent;
import com.auction.infrastructure.persistence.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuctionApplicationService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public AuctionApplicationService(AuctionRepository auctionRepository,
                                     BidRepository bidRepository,
                                     OutboxEventRepository outboxEventRepository,
                                     ObjectMapper objectMapper) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID createAuction(CreateAuctionCommand command) {
        UUID id = UUID.randomUUID();
        Auction auction = Auction.create(id, command, Instant.now());
        AuctionEntity entity = new AuctionEntity(
                id, command.sellerId(), command.title(), command.description(), command.category(),
                command.lotQuantity() > 0 ? command.lotQuantity() : 1,
                command.lotUnit() != null ? command.lotUnit() : "unit",
                new BigDecimal(command.startingPrice()), command.currency(),
                new BigDecimal(command.minimumIncrement()),
                command.reservePrice() != null ? new BigDecimal(command.reservePrice()) : null,
                command.buyNowPrice() != null ? new BigDecimal(command.buyNowPrice()) : null,
                command.startsAt(), command.endsAt(),
                command.antiSnipingSeconds() > 0 ? command.antiSnipingSeconds() : 120,
                auction.getStatus());
        entity.setAggregateVersion(auction.getVersion());
        auctionRepository.save(entity);
        saveOutbox(auction.getPendingEvents());
        auction.clearPendingEvents();
        return id;
    }

    @Transactional
    public void openAuction(OpenAuctionCommand command) {
        AuctionEntity entity = load(command.auctionId());
        Auction auction = reconstitute(entity);
        auction.open(Instant.now());
        entity.setStatus(auction.getStatus());
        entity.setAggregateVersion(auction.getVersion());
        saveOutbox(auction.getPendingEvents());
        auction.clearPendingEvents();
    }

    @Transactional
    public void placeBid(PlaceBidCommand command) {
        AuctionEntity entity = load(command.auctionId());
        Auction auction = reconstitute(entity);
        auction.placeBid(command.bidderId(), Money.of(command.amount(), command.currency()), Instant.now());

        entity.setStatus(auction.getStatus());
        entity.setCurrentPrice(auction.getCurrentPrice() != null ? auction.getCurrentPrice().amount() : null);
        entity.setHighestBidderId(auction.getHighestBidderId());
        entity.setEndsAt(auction.getEndsAt());
        entity.setAggregateVersion(auction.getVersion());

        List<Bid> bids = auction.getBids();
        if (!bids.isEmpty()) {
            Bid lastBid = bids.getLast();
            bidRepository.save(new BidEntity(lastBid.bidId(), entity, lastBid.bidderId(),
                    lastBid.amount().amount(), lastBid.amount().currency().getCurrencyCode(), lastBid.placedAt()));
        }

        saveOutbox(auction.getPendingEvents());
        auction.clearPendingEvents();
    }

    @Transactional
    public void closeAuction(CloseAuctionCommand command) {
        AuctionEntity entity = load(command.auctionId());
        Auction auction = reconstitute(entity);
        auction.close(Instant.now());
        entity.setStatus(auction.getStatus());
        entity.setAggregateVersion(auction.getVersion());
        saveOutbox(auction.getPendingEvents());
        auction.clearPendingEvents();
    }

    @Transactional
    public void cancelAuction(CancelAuctionCommand command) {
        AuctionEntity entity = load(command.auctionId());
        Auction auction = reconstitute(entity);
        auction.cancel(Instant.now());
        entity.setStatus(auction.getStatus());
        entity.setAggregateVersion(auction.getVersion());
        saveOutbox(auction.getPendingEvents());
        auction.clearPendingEvents();
    }

    private AuctionEntity load(UUID id) {
        return auctionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Auction not found: " + id));
    }

    private Auction reconstitute(AuctionEntity entity) {
        List<Bid> bids = List.of();
        return Auction.reconstitute(
                entity.getId(),
                entity.getAggregateVersion(),
                entity.getSellerId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getCategory(),
                entity.getLotQuantity(),
                entity.getLotUnit(),
                Money.of(entity.getStartingPrice().toPlainString(), entity.getCurrency()),
                Money.of(entity.getMinimumIncrement().toPlainString(), entity.getCurrency()),
                entity.getReservePrice() != null ? Money.of(entity.getReservePrice().toPlainString(), entity.getCurrency()) : null,
                entity.getBuyNowPrice() != null ? Money.of(entity.getBuyNowPrice().toPlainString(), entity.getCurrency()) : null,
                entity.getStartsAt(),
                entity.getEndsAt(),
                entity.getAntiSnipingSeconds(),
                entity.getStatus(),
                entity.getCurrentPrice() != null ? Money.of(entity.getCurrentPrice().toPlainString(), entity.getCurrency()) : null,
                entity.getHighestBidderId(),
                bids);
    }

    private void saveOutbox(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            String payload;
            try {
                payload = objectMapper.writeValueAsString(event);
            } catch (Exception e) {
                throw new IllegalStateException("Could not serialize event", e);
            }
            outboxEventRepository.save(new OutboxEventEntity(
                    event.eventId(), event.aggregateId(), "Auction", event.eventType(),
                    payload, event.aggregateVersion(), event.occurredAt()));
        }
    }
}
