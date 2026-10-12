package com.auction.infrastructure.messaging.adapter;

import com.auction.application.port.out.EventPublisherPort;
import com.auction.infrastructure.messaging.AuctionMessaging;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;

/**
 * Adapter for the event broker.
 * Swap the body for SNS + one subscription per consumer when more than one
 * read model needs the same event stream.
 */
@Component
public class SqsEventPublisherAdapter implements EventPublisherPort {

    private final SqsClient sqsClient;
    private final AuctionMessaging messaging;

    public SqsEventPublisherAdapter(SqsClient sqsClient, AuctionMessaging messaging) {
        this.sqsClient = sqsClient;
        this.messaging = messaging;
    }

    @Override
    public boolean isReady() {
        return messaging.getQueueUrl() != null;
    }

    @Override
    public void publish(String eventType, String payload) {
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(messaging.getQueueUrl())
                .messageBody(payload)
                .messageAttributes(Map.of("event_type", MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(eventType)
                        .build()))
                .build());
    }
}