package com.auction.application.projection;

import com.auction.application.port.out.AuctionSummaryRecord;
import com.auction.application.port.out.AuctionSummaryStorePort;
import com.auction.domain.auction.AuctionStatus;
import com.auction.infrastructure.messaging.DomainEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Applies events to the AuctionSummary read model.
 * Idempotent: an event whose version is not greater than the stored one is ignored.
 */
@Component
public class AuctionSummaryProjector {

    private static final Logger log = LoggerFactory.getLogger(AuctionSummaryProjector.class);

    private final AuctionSummaryStorePort store;

    public AuctionSummaryProjector(AuctionSummaryStorePort store) {
        this.store = store;
    }

    @Transactional
    public void handle(DomainEventMessage event) {
        JsonNode payload = event.payload();
        long version = event.aggregateVersion();

        switch (event.eventType()) {
            case "AuctionCreated" -> store.save(new AuctionSummaryRecord(
                    event.aggregateId(),
                    UUID.fromString(payload.path("sellerId").asString()),
                    payload.path("title").asString(),
                    payload.path("category").asString(),
                    AuctionStatus.DRAFT.name(),
                    payload.path("currency").asString(),
                    null, null, 0,
                    Instant.parse(payload.path("endsAt").asString()),
                    version, Instant.now()));
            case "AuctionOpened" -> update(event.aggregateId(), r -> {
                if (version <= r.lastEventVersion()) {
                    return r;
                }
                return new AuctionSummaryRecord(r.auctionId(), r.sellerId(), r.title(), r.category(),
                        AuctionStatus.OPEN.name(), r.currency(), r.currentPrice(),
                        r.highestBidderId(), r.bidCount(), r.endsAt(), version, Instant.now());
            });
            case "BidPlaced" -> update(event.aggregateId(), r -> {
                if (version <= r.lastEventVersion()) {
                    return r;
                }
                return new AuctionSummaryRecord(r.auctionId(), r.sellerId(), r.title(), r.category(),
                        r.status(), r.currency(), new BigDecimal(payload.path("amount").asString()),
                        UUID.fromString(payload.path("bidderId").asString()),
                        r.bidCount() + 1, r.endsAt(), version, Instant.now());
            });
            case "AuctionExtended" -> update(event.aggregateId(), r -> {
                if (version <= r.lastEventVersion()) {
                    return r;
                }
                return new AuctionSummaryRecord(r.auctionId(), r.sellerId(), r.title(), r.category(),
                        r.status(), r.currency(), r.currentPrice(), r.highestBidderId(), r.bidCount(),
                        Instant.parse(payload.path("newEndsAt").asString()), version, Instant.now());
            });
            case "AuctionClosed" -> {
                boolean sold = payload.path("sold").asBoolean();
                String winner = payload.path("winningBidderId").asString(null);
                update(event.aggregateId(), r -> {
                    if (version <= r.lastEventVersion()) {
                        return r;
                    }
                    return new AuctionSummaryRecord(r.auctionId(), r.sellerId(), r.title(), r.category(),
                            sold ? AuctionStatus.SOLD.name() : AuctionStatus.UNSOLD.name(),
                            r.currency(),
                            payload.path("finalPrice").isNull() ? null : new BigDecimal(payload.path("finalPrice").asString()),
                            winner != null ? UUID.fromString(winner) : r.highestBidderId(),
                            r.bidCount(), r.endsAt(), version, Instant.now());
                });
            }
            case "AuctionCancelled" -> update(event.aggregateId(), r -> {
                if (version <= r.lastEventVersion()) {
                    return r;
                }
                return new AuctionSummaryRecord(r.auctionId(), r.sellerId(), r.title(), r.category(),
                        AuctionStatus.CANCELLED.name(), r.currency(), r.currentPrice(),
                        r.highestBidderId(), r.bidCount(), r.endsAt(), version, Instant.now());
            });
            default -> log.warn("Unhandled event type for AuctionSummary: {}", event.eventType());
        }
    }

    private void update(UUID auctionId, UnaryOperator<AuctionSummaryRecord> change) {
        store.findById(auctionId).ifPresent(current -> store.save(change.apply(current)));
    }
}