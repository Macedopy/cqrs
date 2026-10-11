package com.auction.infrastructure.messaging;

import com.auction.infrastructure.persistence.OutboxEventEntity;
import com.auction.infrastructure.persistence.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.List;
import java.util.Map;

@Service
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final SqsClient sqsClient;
    private final AuctionMessaging messaging;

    public OutboxEventPublisher(OutboxEventRepository outboxEventRepository,
                                SqsClient sqsClient, AuctionMessaging messaging) {
        this.outboxEventRepository = outboxEventRepository;
        this.sqsClient = sqsClient;
        this.messaging = messaging;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPending() {
        String queueUrl = messaging.getQueueUrl();
        if (queueUrl == null) {
            return;
        }
        List<OutboxEventEntity> events = outboxEventRepository.findByPublishedAtIsNullOrderByOccurredAtAsc();
        for (OutboxEventEntity event : events) {
            try {
                sqsClient.sendMessage(SendMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .messageBody(event.getPayload())
                        .messageAttributes(Map.of("event_type", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(event.getEventType())
                                .build()))
                        .build());
                event.markPublished();
                log.info("Published event {} id={}", event.getEventType(), event.getId());
            } catch (Exception e) {
                event.incrementAttempts();
                log.error("Failed to publish event {}", event.getEventType(), e);
            }
        }
    }
}