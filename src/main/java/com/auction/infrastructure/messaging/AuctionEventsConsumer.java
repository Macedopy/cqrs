package com.auction.infrastructure.messaging;

import com.auction.application.projection.AuctionSummaryProjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuctionEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuctionEventsConsumer.class);

    private final SqsClient sqsClient;
    private final AuctionMessaging messaging;
    private final AuctionSummaryProjector projector;
    private final ObjectMapper objectMapper;

    public AuctionEventsConsumer(SqsClient sqsClient, AuctionMessaging messaging,
                                 AuctionSummaryProjector projector, ObjectMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.messaging = messaging;
        this.projector = projector;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 3000)
    public void consume() {
        String queueUrl = messaging.getQueueUrl();
        if (queueUrl == null) {
            return;
        }
        ReceiveMessageResponse response = sqsClient.receiveMessage(r -> r
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(2));
        for (Message message : response.messages()) {
            try {
                String eventType = message.messageAttributes().get("event_type") != null
                        ? message.messageAttributes().get("event_type").stringValue()
                        : null;
                DomainEventMessage event = DomainEventMessage.parse(message.body(), eventType, objectMapper);
                projector.handle(event);
                log.info("Projected event {} v{} for auction {}", event.eventType(),
                        event.aggregateVersion(), event.aggregateId());
            } catch (Exception e) {
                log.error("Failed to process message {}", message.messageId(), e);
                continue;
            }
            sqsClient.deleteMessage(r -> r.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
        }
    }
}