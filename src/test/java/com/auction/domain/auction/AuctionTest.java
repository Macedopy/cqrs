package com.auction.domain.auction;

import com.auction.domain.auction.commands.CancelAuctionCommand;
import com.auction.domain.auction.commands.CloseAuctionCommand;
import com.auction.domain.auction.commands.CreateAuctionCommand;
import com.auction.domain.auction.commands.OpenAuctionCommand;
import com.auction.domain.auction.events.*;
import com.auction.domain.auction.exceptions.AuctionNotOpenException;
import com.auction.domain.auction.exceptions.InvalidBidException;
import com.auction.domain.auction.exceptions.InvalidStateTransitionException;
import com.auction.domain.shared.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain unit tests: no Spring, no database, no HTTP.
 * This is the payoff of keeping the rules inside the aggregate.
 */
class AuctionTest {

    private static final UUID SELLER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BIDDER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final Instant now = Instant.parse("2026-10-10T12:00:00Z");
    private final Instant startsAt = now.minusSeconds(60);
    private final Instant endsAt = now.plusSeconds(3600);

    private Auction auction;

    @BeforeEach
    void setUp() {
        auction = Auction.create(UUID.randomUUID(), baseCommand(), now);
        auction.clearPendingEvents();
    }

    private CreateAuctionCommand baseCommand() {
        return new CreateAuctionCommand(SELLER, "Boeing 737-800", "2015, 48000h",
                AuctionCategory.AIRCRAFT, 1, "aircraft", "5000000", "BRL", "50000",
                "7000000", null, startsAt, endsAt, 120);
    }

    @Nested
    @DisplayName("creation")
    class Creation {

        @Test
        @DisplayName("starts as DRAFT and emits AuctionCreated at version 1")
        void createsAsDraft() {
            Auction fresh = Auction.create(UUID.randomUUID(), baseCommand(), now);

            assertThat(fresh.getStatus()).isEqualTo(AuctionStatus.DRAFT);
            assertThat(fresh.getPendingEvents()).hasSize(1);
            assertThat(fresh.getPendingEvents().getFirst())
                    .isInstanceOf(AuctionCreated.class)
                    .extracting(DomainEvent::aggregateVersion)
                    .isEqualTo(1L);
        }

        @Test
        @DisplayName("rejects an end time that is not after the start time")
        void rejectsInvertedWindow() {
            assertThatThrownBy(() -> Auction.create(UUID.randomUUID(), new CreateAuctionCommand(
                    SELLER, "Broken", null, AuctionCategory.SINGLE_ITEM, 1, "unit",
                    "100", "BRL", "10", null, null, endsAt, startsAt, 120), now))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("rejects a buy-now price below the starting price")
        void rejectsInvalidBuyNow() {
            assertThatThrownBy(() -> Auction.create(UUID.randomUUID(), new CreateAuctionCommand(
                    SELLER, "Broken", null, AuctionCategory.SINGLE_ITEM, 1, "unit",
                    "1000", "BRL", "10", null, "500", startsAt, endsAt, 120), now))
                    .isInstanceOf(InvalidBidException.class)
                    .hasMessageContaining("greater than the starting price");
        }
    }

    @Nested
    @DisplayName("opening")
    class Opening {

        @Test
        @DisplayName("moves DRAFT to OPEN and emits AuctionOpened")
        void opens() {
            auction.open(now);

            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.OPEN);
            assertThat(auction.getPendingEvents()).hasSize(1)
                    .first().isInstanceOf(AuctionOpened.class);
        }

        @Test
        @DisplayName("cannot be opened before the start time")
        void rejectsEarlyOpen() {
            Auction future = Auction.create(UUID.randomUUID(), new CreateAuctionCommand(
                    SELLER, "Future", null, AuctionCategory.SINGLE_ITEM, 1, "unit",
                    "100", "BRL", "10", null, null, now.plusSeconds(600), now.plusSeconds(3600), 120), now);

            assertThatThrownBy(() -> future.open(now))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("before its start time");
        }

        @Test
        @DisplayName("cannot be opened twice")
        void rejectsDoubleOpen() {
            auction.open(now);

            assertThatThrownBy(() -> auction.open(now))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("Only draft auctions");
        }
    }

    @Nested
    @DisplayName("bidding")
    class Bidding {

        @BeforeEach
        void openAuction() {
            auction.open(now);
            auction.clearPendingEvents();
        }

        @Test
        @DisplayName("accepts the first bid at the starting price")
        void acceptsFirstBid() {
            auction.placeBid(BIDDER, Money.of("5000000", "BRL"), now.plusSeconds(30));

            assertThat(auction.getCurrentPrice().amount().toPlainString()).isEqualTo("5000000");
            assertThat(auction.getHighestBidderId()).isEqualTo(BIDDER);
            assertThat(auction.getPendingEvents()).first().isInstanceOf(BidPlaced.class);
        }

        @Test
        @DisplayName("rejects a bid below the minimum increment")
        void rejectsLowBid() {
            auction.placeBid(BIDDER, Money.of("5000000", "BRL"), now.plusSeconds(30));

            assertThatThrownBy(() -> auction.placeBid(UUID.randomUUID(),
                    Money.of("5049999", "BRL"), now.plusSeconds(60)))
                    .isInstanceOf(InvalidBidException.class)
                    .hasMessageContaining("at least 5050000");
        }

        @Test
        @DisplayName("rejects the seller bidding on their own auction")
        void rejectsSellerBid() {
            assertThatThrownBy(() -> auction.placeBid(SELLER, Money.of("5000000", "BRL"), now.plusSeconds(30)))
                    .isInstanceOf(InvalidBidException.class)
                    .hasMessageContaining("seller cannot bid");
        }

        @Test
        @DisplayName("rejects a bid after the auction ended")
        void rejectsLateBid() {
            assertThatThrownBy(() -> auction.placeBid(BIDDER, Money.of("5000000", "BRL"), endsAt))
                    .isInstanceOf(InvalidBidException.class)
                    .hasMessageContaining("already ended");
        }

        @Test
        @DisplayName("rejects a bid while the auction is not open")
        void rejectsBidOnDraft() {
            Auction draft = Auction.create(UUID.randomUUID(), baseCommand(), now);

            assertThatThrownBy(() -> draft.placeBid(BIDDER, Money.of("5000000", "BRL"), now))
                    .isInstanceOf(AuctionNotOpenException.class);
        }

        @Test
        @DisplayName("rejects a low bid after reconstitution, even with an empty bid history")
        void rejectsLowBidAfterReconstitution() {
            auction.placeBid(BIDDER, Money.of("5000000", "BRL"), now.plusSeconds(30));

            // Simulates loading from the database: the bid list is not hydrated.
            Auction reloaded = Auction.reconstitute(
                    auction.getId(), auction.getVersion(), SELLER, "Boeing 737-800",
                    "2015, 48000h", AuctionCategory.AIRCRAFT, 1, "aircraft",
                    Money.of("5000000", "BRL"), Money.of("50000", "BRL"),
                    Money.of("7000000", "BRL"), null,
                    startsAt, endsAt, 120,
                    AuctionStatus.OPEN,
                    Money.of("5000000", "BRL"), BIDDER, java.util.List.of());

            assertThatThrownBy(() -> reloaded.placeBid(UUID.randomUUID(),
                    Money.of("5049999", "BRL"), now.plusSeconds(60)))
                    .isInstanceOf(InvalidBidException.class)
                    .hasMessageContaining("at least 5050000");
        }

        @Test
        @DisplayName("increments the version on every emitted event")
        void versionsAreSequential() {
            auction.placeBid(BIDDER, Money.of("5000000", "BRL"), now.plusSeconds(30));
            auction.placeBid(UUID.randomUUID(), Money.of("5050000", "BRL"), now.plusSeconds(60));

            assertThat(auction.getPendingEvents())
                    .extracting(DomainEvent::aggregateVersion)
                    .containsExactly(3L, 4L);
        }
    }

    @Nested
    @DisplayName("anti-sniping")
    class AntiSniping {

        @Test
        @DisplayName("extends the end time when a bid lands in the final window")
        void extendsOnLateBid() {
            auction.open(now);
            Instant lastMoment = endsAt.minusSeconds(30);

            auction.placeBid(BIDDER, Money.of("5000000", "BRL"), lastMoment);

            assertThat(auction.getEndsAt()).isEqualTo(endsAt.plusSeconds(120));
            assertThat(auction.getPendingEvents())
                    .anyMatch(AuctionExtended.class::isInstance);
        }

        @Test
        @DisplayName("does not extend when the bid is far from the end")
        void doesNotExtendEarly() {
            auction.open(now);

            auction.placeBid(BIDDER, Money.of("5000000", "BRL"), now.plusSeconds(30));

            assertThat(auction.getEndsAt()).isEqualTo(endsAt);
        }
    }

    @Nested
    @DisplayName("closing")
    class Closing {

        @Test
        @DisplayName("is UNSOLD when no bid reached the reserve price")
        void unsoldBelowReserve() {
            auction.open(now);
            auction.placeBid(BIDDER, Money.of("5000000", "BRL"), now.plusSeconds(30));

            auction.close(endsAt.plusSeconds(1));

            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.UNSOLD);
            assertThat(auction.getPendingEvents()).last()
                    .isInstanceOf(AuctionClosed.class)
                    .extracting(e -> ((AuctionClosed) e).reason())
                    .isEqualTo("RESERVE_NOT_MET");
        }

        @Test
        @DisplayName("is SOLD when the reserve price was met")
        void soldWhenReserveMet() {
            Auction generous = Auction.create(UUID.randomUUID(), new CreateAuctionCommand(
                    SELLER, "No reserve", null, AuctionCategory.SINGLE_ITEM, 1, "unit",
                    "1000", "BRL", "100", null, null, startsAt, endsAt, 120), now);
            generous.open(now);
            generous.placeBid(BIDDER, Money.of("1000", "BRL"), now.plusSeconds(30));

            generous.close(endsAt.plusSeconds(1));

            assertThat(generous.getStatus()).isEqualTo(AuctionStatus.SOLD);
        }

        @Test
        @DisplayName("is UNSOLD with reason NO_BIDS when nobody bid")
        void unsoldWithoutBids() {
            auction.open(now);

            auction.close(endsAt.plusSeconds(1));

            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.UNSOLD);
            assertThat(auction.getPendingEvents()).last()
                    .isInstanceOf(AuctionClosed.class)
                    .extracting(e -> ((AuctionClosed) e).reason())
                    .isEqualTo("NO_BIDS");
        }

        @Test
        @DisplayName("cannot be closed twice")
        void rejectsDoubleClose() {
            auction.open(now);
            auction.close(now.plusSeconds(10));

            assertThatThrownBy(() -> auction.close(now.plusSeconds(20)))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("cancelling")
    class Cancelling {

        @Test
        @DisplayName("moves DRAFT to CANCELLED")
        void cancelsDraft() {
            auction.cancel(now);

            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
            assertThat(auction.getPendingEvents()).first().isInstanceOf(AuctionCancelled.class);
        }

        @Test
        @DisplayName("cannot cancel an auction that already sold")
        void rejectsCancelAfterSold() {
            Auction generous = Auction.create(UUID.randomUUID(), new CreateAuctionCommand(
                    SELLER, "No reserve", null, AuctionCategory.SINGLE_ITEM, 1, "unit",
                    "1000", "BRL", "100", null, null, startsAt, endsAt, 120), now);
            generous.open(now);
            generous.placeBid(BIDDER, Money.of("1000", "BRL"), now.plusSeconds(30));
            generous.close(now.plusSeconds(40));

            assertThatThrownBy(() -> generous.cancel(now.plusSeconds(50)))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }
}