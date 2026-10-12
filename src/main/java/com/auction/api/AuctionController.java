package com.auction.api;

import com.auction.application.dto.AuctionDetailsResponse;
import com.auction.application.dto.CreateAuctionRequest;
import com.auction.application.dto.PlaceBidRequest;
import com.auction.application.port.in.AuctionCommandUseCase;
import com.auction.application.port.out.AuctionRecord;
import com.auction.application.port.out.AuctionStorePort;
import com.auction.domain.auction.commands.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * HTTP adapter. Depends on the driving port, not on the service implementation.
 */
@RestController
@RequestMapping("/auctions")
public class AuctionController {

    private final AuctionCommandUseCase commandUseCase;
    private final AuctionStorePort auctionStore;

    public AuctionController(AuctionCommandUseCase commandUseCase, AuctionStorePort auctionStore) {
        this.commandUseCase = commandUseCase;
        this.auctionStore = auctionStore;
    }

    @PostMapping
    public ResponseEntity<UUID> create(@RequestBody CreateAuctionRequest request) {
        UUID id = commandUseCase.createAuction(new CreateAuctionCommand(
                UUID.fromString(request.sellerId()), request.title(), request.description(),
                request.category(), request.lotQuantity(), request.lotUnit(),
                request.startingPrice(), request.currency(), request.minimumIncrement(),
                request.reservePrice(), request.buyNowPrice(), request.startsAt(),
                request.endsAt(), request.antiSnipingSeconds()));
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    @PostMapping("/{id}/open")
    public ResponseEntity<Void> open(@PathVariable UUID id) {
        commandUseCase.openAuction(new OpenAuctionCommand(id));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/bids")
    public ResponseEntity<Void> placeBid(@PathVariable UUID id, @RequestBody PlaceBidRequest request) {
        commandUseCase.placeBid(new PlaceBidCommand(id, UUID.fromString(request.bidderId()),
                request.amount(), request.currency()));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Void> close(@PathVariable UUID id) {
        commandUseCase.closeAuction(new CloseAuctionCommand(id));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        commandUseCase.cancelAuction(new CancelAuctionCommand(id));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuctionDetailsResponse> get(@PathVariable UUID id) {
        return auctionStore.findById(id)
                .map(AuctionController::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static AuctionDetailsResponse toResponse(AuctionRecord record) {
        return new AuctionDetailsResponse(
                record.id(), record.sellerId(), record.title(), record.description(),
                record.category(), record.lotQuantity(), record.lotUnit(), record.startingPrice(),
                record.currency(), record.minimumIncrement(), record.reservePrice(),
                record.buyNowPrice(), record.startsAt(), record.endsAt(), record.status().name(),
                record.currentPrice(), record.highestBidderId(), record.aggregateVersion());
    }
}