package com.auction.application.service;

import com.auction.application.AuctionNotFoundException;
import com.auction.application.port.out.*;
import com.auction.domain.auction.AuctionStatus;
import com.auction.domain.auction.commands.CreateAuctionCommand;
import com.auction.domain.auction.commands.PlaceBidCommand;
import com.auction.domain.auction.exceptions.InvalidBidException;
import com.auction.domain.auction.exceptions.AuctionNotOpenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Application service test with the driven ports mocked.
 * No database, no broker, no Spring context: only the orchestration is verified.
 */
class AuctionApplicationServiceTest {

    private static final UUID SELLER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BIDDER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private AuctionStorePort auctionStore;
    private BidStorePort bidStore;
    private OutboxStorePort outboxStore;
    private AuctionApplicationService service;

    @BeforeEach
    void setUp() {
        auctionStore = mock(AuctionStorePort.class);
        bidStore = mock(BidStorePort.class);
        outboxStore = mock(OutboxStorePort.class);
        service = new AuctionApplicationService(auctionStore, bidStore, outboxStore,
                JsonMapper.builder().findAndAddModules().build());
    }

    private AuctionRecord openRecord() {
        return new AuctionRecord(UUID.randomUUID(), SELLER, "Cattle lot", "Nelore",
                "LIVESTOCK", 200, "head", new BigDecimal("100000"), "BRL",
                new BigDecimal("5000"), new BigDecimal("200000"), null,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), 120,
                AuctionStatus.OPEN, null, null, 2L);
    }

    @Test
    @DisplayName("placeBid persists the bid, updates the auction and appends the event")
    void placeBidPersistsEverything() {
        AuctionRecord record = openRecord();
        when(auctionStore.findById(record.id())).thenReturn(Optional.of(record));

        service.placeBid(new PlaceBidCommand(record.id(), BIDDER, "105000", "BRL"));

        verify(auctionStore).save(argThat(saved ->
                saved.currentPrice().compareTo(new BigDecimal("105000")) == 0
                        && BIDDER.equals(saved.highestBidderId())
                        && saved.aggregateVersion() == 3L));
        verify(bidStore).save(argThat(bid ->
                record.id().equals(bid.auctionId())
                        && BIDDER.equals(bid.bidderId())
                        && "BRL".equals(bid.currency())));
        verify(outboxStore).append(argThat(event ->
                "BidPlaced".equals(event.eventType())
                        && record.id().equals(event.aggregateId())
                        && event.aggregateVersion() == 3L));
    }

    @Test
    @DisplayName("placeBid never touches the stores when the domain rejects the command")
    void doesNotPersistOnDomainFailure() {
        AuctionRecord record = openRecord();
        when(auctionStore.findById(record.id())).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.placeBid(
                new PlaceBidCommand(record.id(), BIDDER, "1", "BRL")))
                .isInstanceOf(InvalidBidException.class);

        verify(bidStore, never()).save(any());
        verify(outboxStore, never()).append(any());
    }

    @Test
    @DisplayName("placeBid rejects a bid on a non-open auction")
    void rejectsBidWhenNotOpen() {
        AuctionRecord draft = new AuctionRecord(UUID.randomUUID(), SELLER, "Cattle lot", null,
                "LIVESTOCK", 200, "head", new BigDecimal("100000"), "BRL",
                new BigDecimal("5000"), null, null,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), 120,
                AuctionStatus.DRAFT, null, null, 1L);
        when(auctionStore.findById(draft.id())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.placeBid(
                new PlaceBidCommand(draft.id(), BIDDER, "105000", "BRL")))
                .isInstanceOf(AuctionNotOpenException.class);
    }

    @Test
    @DisplayName("createAuction writes the state and the creation event")
    void createAuctionWritesStateAndEvent() {
        when(auctionStore.save(any())).thenAnswer(i -> i.getArgument(0));

        UUID id = service.createAuction(new CreateAuctionCommand(SELLER, "Tractor", "2022",
                "VEHICLE", 1, "unit", "450000", "BRL", "10000", "600000", null,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), 120));

        assertThat(id).isNotNull();
        verify(auctionStore).save(argThat(saved ->
                saved.id().equals(id) && saved.status() == AuctionStatus.DRAFT
                        && saved.aggregateVersion() == 1L));
        verify(outboxStore).append(argThat(event ->
                "AuctionCreated".equals(event.eventType()) && event.aggregateVersion() == 1L));
    }

    @Test
    @DisplayName("placeBid fails when the auction does not exist")
    void failsWhenAuctionMissing() {
        when(auctionStore.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.placeBid(
                new PlaceBidCommand(UUID.randomUUID(), BIDDER, "100", "BRL")))
                .isInstanceOf(AuctionNotFoundException.class)
                .hasMessageContaining("Auction not found");
    }
}