package com.auction.application.port.out;

import java.util.UUID;

/** Driven port: transactional outbox. Implemented in infrastructure. */
public interface OutboxStorePort {

    void append(OutboxRecord event);
}