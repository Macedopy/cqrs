package com.auction.api;

import com.auction.application.dto.CreateAuctionRequest;
import com.auction.application.dto.PlaceBidRequest;
import com.auction.application.service.AuctionApplicationService;
import com.auction.domain.auction.commands.*;
import com.auction.infrastructure.persistence.AuctionEntity;
import com.auction.infrastructure.persistence.AuctionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/auctions")
public class AuctionController {

    private final AuctionApplicationService service;
    private final AuctionRepository auctionRepository;

    public AuctionController(AuctionApplicationService service, AuctionRepository auctionRepository) {
        this.service = service;
        this.auctionRepository = auctionRepository;
    }

    @PostMapping
    public ResponseEntity<UUID> create(@RequestBody CreateAuctionRequest request) {
        UUID id = service.createAuction(new CreateAuctionCommand(
                UUID.fromString(request.sellerId()), request.title(), request.description(), request.category(),
                request.lotQuantity(), request.lotUnit(), request.startingPrice(), request.currency(),
                request.minimumIncrement(), request.reservePrice(), request.buyNowPrice(),
                request.startsAt(), request.endsAt(), request.antiSnipingSeconds()));
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    @PostMapping("/{id}/open")
    public ResponseEntity<Void> open(@PathVariable UUID id) {
        service.openAuction(new OpenAuctionCommand(id));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/bids")
    public ResponseEntity<Void> placeBid(@PathVariable UUID id, @RequestBody PlaceBidRequest request) {
        service.placeBid(new PlaceBidCommand(id, UUID.fromString(request.bidderId()), request.amount(), request.currency()));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Void> close(@PathVariable UUID id) {
        service.closeAuction(new CloseAuctionCommand(id));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        service.cancelAuction(new CancelAuctionCommand(id));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuctionEntity> get(@PathVariable UUID id) {
        return auctionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
