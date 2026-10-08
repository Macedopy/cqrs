package com.auction.domain.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public abstract class AggregateRoot {

    private final UUID id;
    private long version;
    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    protected AggregateRoot(UUID id, long version) {
        this.id = id;
        this.version = version;
    }

    protected void registerEvent(DomainEvent event) {
        pendingEvents.add(event);
        this.version = Math.max(this.version, event.aggregateVersion());
    }

    public UUID getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public List<DomainEvent> getPendingEvents() {
        return Collections.unmodifiableList(pendingEvents);
    }

    public void clearPendingEvents() {
        pendingEvents.clear();
    }
}
