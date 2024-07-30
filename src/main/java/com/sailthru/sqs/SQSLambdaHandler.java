package com.sailthru.sqs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.slf4j.impl.SimpleLogger;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import com.sailthru.sqs.exception.NoRetryException;
import com.sailthru.sqs.exception.RetryLaterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SQSLambdaHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {
    private static Logger LOGGER;
    private String QUEUE_URL;
    private int BASE_TIMEOUT;
    private int TIMEOUT_FACTOR;

    private final SqsClient sqsClient;
    private MessageProcessor messageProcessor;

    static {
        initializeLogger();
    }

    public SQSLambdaHandler() {
        initializeSystemVars();
        this.messageProcessor = new MessageProcessor();
        this.sqsClient = SqsClient.builder().region(Region.US_EAST_1).build();
    }

    // used for test code
    SQSLambdaHandler(SqsClient sqsClient, String queueUrl, int baseTimeout, int timeoutFactor) {
        this.messageProcessor = new MessageProcessor();
        this.sqsClient = sqsClient;
        this.QUEUE_URL = queueUrl;
        this.BASE_TIMEOUT = baseTimeout;
        this.TIMEOUT_FACTOR = timeoutFactor;
    }

    @Override
    public SQSBatchResponse handleRequest(final SQSEvent event, final Context context) {
        final List<SQSBatchResponse.BatchItemFailure> batchItemFailures = new ArrayList<>();
        final List<FailedRequest> failedRequestList = new ArrayList<>();

        event.getRecords().forEach(sqsMessage -> {
            try {
                getMessageProcessor().process(sqsMessage);
            } catch (NoRetryException e) {
                LOGGER.error("Non-retryable exception occurred processing message id {} because of: {}.",
                        sqsMessage.getMessageId(),
                        e.getMessage(), e);
            } catch (RetryLaterException e) {
                LOGGER.error("Retryable exception occurred processing message id {} " +
                                "because of exception: {}. Will retry.", sqsMessage.getMessageId(), e.getMessage(), e);
                FailedRequest failedRequest = new FailedRequest(sqsMessage.getMessageId(), e.getResponseCode(),
                        e.getRetryAfter(), sqsMessage.getReceiptHandle(), getApproximateReceiveCount(sqsMessage));

                failedRequestList.add(failedRequest);
                batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(sqsMessage.getMessageId()));
            }
        });

        processFailedRequests(failedRequestList);
        return new SQSBatchResponse(batchItemFailures);
    }

    int getApproximateReceiveCount(SQSEvent.SQSMessage sqsMessage) {
        final int defaultValue = 1;
        try {
            if (sqsMessage.getAttributes() == null || sqsMessage.getAttributes().isEmpty()) {
                return defaultValue;
            }
            String value = sqsMessage.getAttributes().get("ApproximateReceiveCount");
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void processFailedRequests(List<FailedRequest> failedRequestList) {
        failedRequestList.forEach(this::processFailedRequest);
    }

    private void processFailedRequest(FailedRequest failedRequest) {
        try {
            final int visibilityTimeout = failedRequest.getRetryAfter() > 0 ?
                    failedRequest.getRetryAfter() : calculateVisibilityTimeout(failedRequest.getReceiveCount());
            setVisibilityTimeout(failedRequest.getReceiptHandle(), visibilityTimeout);
        } catch (Exception e) {
            LOGGER.debug("Change visibility timeout error: {}", e.getMessage(), e);
        }
    }

    void setVisibilityTimeout(String receiptHandle, int visibilityTimeout) {
        ChangeMessageVisibilityRequest changeMessageVisibilityRequest = ChangeMessageVisibilityRequest.builder()
                .queueUrl(QUEUE_URL)
                .receiptHandle(receiptHandle)
                .visibilityTimeout(visibilityTimeout)
                .build();

        sqsClient.changeMessageVisibility(changeMessageVisibilityRequest);
    }

    int calculateVisibilityTimeout(int receiveCount) {
        final double lowerBound = BASE_TIMEOUT * Math.pow(TIMEOUT_FACTOR, receiveCount - 1);
        final double higherBound = BASE_TIMEOUT * Math.pow(TIMEOUT_FACTOR, receiveCount);
        return (int) (Math.random() * (higherBound - lowerBound)) + (int) lowerBound;
    }

    static void initializeLogger() {
        final String levelString = System.getenv("LOG_LEVEL");
        if (levelString != null) {
            System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, levelString);
        }
        LOGGER = LoggerFactory.getLogger(SQSLambdaHandler.class);
    }

    private MessageProcessor getMessageProcessor() {
        return messageProcessor;
    }

    // used for test code
    public void setMessageProcessor(final MessageProcessor messageProcessor) {
        this.messageProcessor = messageProcessor;
    }

    private void initializeSystemVars() {
        final int DEFAULT_BASE_TIMEOUT = 180;
        final int DEFAULT_TIMEOUT_FACTOR = 2;

        QUEUE_URL = System.getenv("SQS_URL");
        if (QUEUE_URL == null || QUEUE_URL.isEmpty()) {
            LOGGER.error("QUEUE URL is not set");
            throw new IllegalArgumentException("QUEUE URL is not set");
        }

        BASE_TIMEOUT = getEnvVarAsInt("BASE_TIMEOUT", DEFAULT_BASE_TIMEOUT);
        TIMEOUT_FACTOR = getEnvVarAsInt("TIMEOUT_FACTOR", DEFAULT_TIMEOUT_FACTOR);

        if (DEFAULT_TIMEOUT_FACTOR > TIMEOUT_FACTOR) {
            LOGGER.error("TIMEOUT_FACTOR is less than BASE_TIMEOUT, adjusting to default values");
            TIMEOUT_FACTOR = DEFAULT_TIMEOUT_FACTOR;
        }
    }

    private static int getEnvVarAsInt(String varName, int defaultValue) {
        try {
            final String value = System.getenv(varName);
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid environment variable for {}: {}. Using default value: {}",
                    varName, System.getenv(varName), defaultValue);
            return defaultValue;
        }
    }
}
