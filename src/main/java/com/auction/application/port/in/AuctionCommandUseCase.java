package com.auction.application.port.in;

import com.auction.domain.auction.commands.*;

import java.util.UUID;

/** Driving port: everything the outside world can ask the write side to do. */
public interface AuctionCommandUseCase {

    UUID createAuction(CreateAuctionCommand command);

    void openAuction(OpenAuctionCommand command);

    void placeBid(PlaceBidCommand command);

    void closeAuction(CloseAuctionCommand command);

    void cancelAuction(CancelAuctionCommand command);
}