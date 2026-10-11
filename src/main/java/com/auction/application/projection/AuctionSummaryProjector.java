package com.auction.application.projection;

import com.auction.domain.auction.AuctionStatus;
import com.auction.infrastructure.messaging.DomainEventMessage;
import com.auction.infrastructure.read.AuctionSummaryEntity;
import com.auction.infrastructure.read.AuctionSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Applies auction events to the AuctionSummary read model.
 * Idempotent: an event whose version is not greater than the stored one is ignored.
 */
@Component
public class AuctionSummaryProjector {

    private static final Logger log = LoggerFactory.getLogger(AuctionSummaryProjector.class);

    private final AuctionSummaryRepository repository;

    public AuctionSummaryProjector(AuctionSummaryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void handle(DomainEventMessage event) {
        JsonNode payload = event.payload();
        UUID aggregateId = event.aggregateId();
        long version = event.aggregateVersion();

        switch (event.eventType()) {
            case "AuctionCreated" -> repository.findById(aggregateId)
                    .orElseGet(() -> repository.save(new AuctionSummaryEntity(
                            aggregateId,
                            UUID.fromString(payload.path("sellerId").asString()),
                            payload.path("title").asString(),
                            payload.path("category").asString(),
                            AuctionStatus.DRAFT.name(),
                            payload.path("currency").asString(),
                            Instant.parse(payload.path("endsAt").asString()),
                            version)));
            case "AuctionOpened" -> update(aggregateId, summary ->
                    summary.applyStatus(AuctionStatus.OPEN, null, null, null, version));
            case "BidPlaced" -> update(aggregateId, summary ->
                    summary.applyBid(new BigDecimal(payload.path("amount").asString()),
                            UUID.fromString(payload.path("bidderId").asString()), version));
            case "AuctionExtended" -> update(aggregateId, summary ->
                    summary.applyStatus(AuctionStatus.OPEN, null, null,
                            Instant.parse(payload.path("newEndsAt").asString()), version));
            case "AuctionClosed" -> {
                boolean sold = payload.path("sold").asBoolean();
                String winner = payload.path("winningBidderId").asString(null);
                update(aggregateId, summary -> summary.applyStatus(
                        sold ? AuctionStatus.SOLD : AuctionStatus.UNSOLD,
                        payload.path("finalPrice").isNull() ? null : new BigDecimal(payload.path("finalPrice").asString()),
                        winner != null ? UUID.fromString(winner) : null,
                        null, version));
            }
            case "AuctionCancelled" -> update(aggregateId, summary ->
                    summary.applyStatus(AuctionStatus.CANCELLED, null, null, null, version));
            default -> log.warn("Unhandled event type for AuctionSummary: {}", event.eventType());
        }
    }

    private void update(UUID aggregateId, Consumer<AuctionSummaryEntity> change) {
        repository.findById(aggregateId).ifPresent(summary -> {
            change.accept(summary);
            repository.save(summary);
        });
    }
}