package com.auction.application.port.out;

/** Driven port: sends events to the broker. Implemented with SQS (or SNS -> SQS). */
public interface EventPublisherPort {

    boolean isReady();

    void publish(String eventType, String payload);
}