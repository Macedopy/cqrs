package com.auction.infrastructure.messaging;

import com.auction.application.port.out.EventPublisherPort;
import com.auction.infrastructure.persistence.OutboxEventEntity;
import com.auction.infrastructure.persistence.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Drains the outbox and hands the events to the broker through the port.
 */
@Service
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final EventPublisherPort eventPublisher;

    public OutboxEventPublisher(OutboxEventRepository outboxEventRepository,
                                EventPublisherPort eventPublisher) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPending() {
        if (!eventPublisher.isReady()) {
            return;
        }
        List<OutboxEventEntity> events = outboxEventRepository.findByPublishedAtIsNullOrderByOccurredAtAsc();
        for (OutboxEventEntity event : events) {
            try {
                eventPublisher.publish(event.getEventType(), event.getPayload());
                event.markPublished();
                log.info("Published event {} id={}", event.getEventType(), event.getId());
            } catch (Exception e) {
                event.incrementAttempts();
                log.error("Failed to publish event {}", event.getEventType(), e);
            }
        }
    }
}