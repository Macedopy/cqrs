package com.auction.infrastructure.persistence.adapter;

import com.auction.application.port.out.OutboxRecord;
import com.auction.application.port.out.OutboxStorePort;
import com.auction.infrastructure.persistence.OutboxEventEntity;
import com.auction.infrastructure.persistence.OutboxEventRepository;
import org.springframework.stereotype.Repository;

@Repository
public class JpaOutboxStoreAdapter implements OutboxStorePort {

    private final OutboxEventRepository repository;

    public JpaOutboxStoreAdapter(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(OutboxRecord event) {
        repository.save(new OutboxEventEntity(
                event.id(),
                event.aggregateId(),
                event.aggregateType(),
                event.eventType(),
                event.payload(),
                event.aggregateVersion(),
                event.occurredAt()));
    }
}