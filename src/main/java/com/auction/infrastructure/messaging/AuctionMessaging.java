package com.auction.infrastructure.messaging;

import org.springframework.stereotype.Component;

@Component
public class AuctionMessaging {

    private volatile String queueUrl;

    public String getQueueUrl() {
        return queueUrl;
    }

    public void setQueueUrl(String queueUrl) {
        this.queueUrl = queueUrl;
    }
}