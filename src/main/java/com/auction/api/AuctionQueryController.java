package com.auction.api;

import com.auction.application.dto.AuctionSummaryResponse;
import com.auction.application.port.in.AuctionQueryUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * HTTP adapter for the read side.
 */
@RestController
@RequestMapping("/queries/auctions")
public class AuctionQueryController {

    private final AuctionQueryUseCase queryUseCase;

    public AuctionQueryController(AuctionQueryUseCase queryUseCase) {
        this.queryUseCase = queryUseCase;
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<AuctionSummaryResponse> summary(@PathVariable UUID id) {
        return queryUseCase.findSummary(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/hot")
    public ResponseEntity<List<AuctionSummaryResponse>> hot(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(queryUseCase.findHotAuctions(limit));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<AuctionSummaryResponse>> mine(@RequestParam UUID bidderId) {
        return ResponseEntity.ok(queryUseCase.findByHighestBidder(bidderId));
    }
}