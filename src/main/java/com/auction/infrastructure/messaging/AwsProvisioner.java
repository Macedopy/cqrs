package com.auction.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

import java.util.Map;

/**
 * Creates the SQS queue and its dead letter queue on startup.
 * Idempotent: existing resources are reused.
 *
 * A topic (SNS) is only needed once more than one consumer subscribes to the same
 * event stream. With a single read model, publishing straight to the queue is enough.
 */
@Component
public class AwsProvisioner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AwsProvisioner.class);

    public static final String QUEUE_NAME = "auction-events-queue";
    public static final String DLQ_NAME = "auction-events-dlq";

    private final SqsClient sqsClient;
    private final AuctionMessaging messaging;

    public AwsProvisioner(SqsClient sqsClient, AuctionMessaging messaging) {
        this.sqsClient = sqsClient;
        this.messaging = messaging;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String dlqUrl = getOrCreateQueue(DLQ_NAME);
            String dlqArn = queueArn(dlqUrl);

            String queueUrl = getOrCreateQueue(QUEUE_NAME);
            sqsClient.setQueueAttributes(SetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributes(Map.of(QueueAttributeName.REDRIVE_POLICY,
                            "{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":\"3\"}"))
                    .build());

            messaging.setQueueUrl(queueUrl);
            log.info("Provisioned queue={} dlq={}", queueUrl, dlqUrl);
        } catch (Exception e) {
            log.error("Failed to provision AWS resources on Floci", e);
        }
    }

    private String queueArn(String queueUrl) {
        return sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.QUEUE_ARN)
                        .build())
                .attributes().get(QueueAttributeName.QUEUE_ARN);
    }

    private String getOrCreateQueue(String name) {
        try {
            return sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl();
        } catch (QueueDoesNotExistException e) {
            return sqsClient.createQueue(CreateQueueRequest.builder().queueName(name).build()).queueUrl();
        }
    }
}