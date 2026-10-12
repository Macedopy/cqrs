package com.auction.infrastructure.persistence.adapter;

import com.auction.application.port.out.BidRecord;
import com.auction.application.port.out.BidStorePort;
import com.auction.infrastructure.persistence.AuctionRepository;
import com.auction.infrastructure.persistence.BidEntity;
import com.auction.infrastructure.persistence.BidRepository;
import org.springframework.stereotype.Repository;

@Repository
public class JpaBidStoreAdapter implements BidStorePort {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;

    public JpaBidStoreAdapter(AuctionRepository auctionRepository, BidRepository bidRepository) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
    }

    @Override
    public void save(BidRecord bid) {
        BidEntity entity = new BidEntity();
        entity.setId(bid.id());
        entity.setAuction(auctionRepository.getReferenceById(bid.auctionId()));
        entity.setBidderId(bid.bidderId());
        entity.setAmount(bid.amount());
        entity.setCurrency(bid.currency());
        entity.setPlacedAt(bid.placedAt());
        bidRepository.save(entity);
    }
}