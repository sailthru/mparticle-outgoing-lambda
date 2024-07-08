package com.sailthru.sqs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.slf4j.impl.SimpleLogger;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import com.sailthru.sqs.exception.NoRetryException;
import com.sailthru.sqs.exception.RetryLaterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SQSLambdaHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {
    private static final String QUEUE_URL = System.getenv("QUEUE_URL"); // Ensure this is set in your environment
    private static final int DEFAULT_MIN_VALUE = 120;
    private static final int DEFAULT_MAX_VALUE = 300;
    private static final SqsClient sqsClient = SqsClient.builder().build();

    private static int minValue;
    private static int maxValue;
    private static final Logger LOGGER = LoggerFactory.getLogger(SQSLambdaHandler.class);

    private MessageProcessor messageProcessor;

    static {
        setupEnvVariables();
    }

    public SQSLambdaHandler() {
        this.messageProcessor = new MessageProcessor();
    }

    @Override
    public SQSBatchResponse handleRequest(final SQSEvent event, final Context context) {
        final List<SQSBatchResponse.BatchItemFailure> batchItemFailures = new ArrayList<>();
        final List<FailedRequest> failedRequestList = new ArrayList<>();

        event.getRecords().forEach(sqsMessage -> {
            try {
                getProcessor().process(sqsMessage);
            } catch (NoRetryException e) {
                LOGGER.error("Non retryable exception occurred processing message id {} because of: {}.",
                        sqsMessage.getMessageId(),
                        e.getMessage(), e);
            } catch (RetryLaterException e) {
                LOGGER.error("Exception occurred processing message id {} because of exception: {}. Will retry.",
                        sqsMessage.getMessageId(), e.getMessage(), e);
                FailedRequest failedRequest = e.getFailedRequest();
                if(failedRequest != null) {
                    failedRequest.setReceiptHandle(sqsMessage.getReceiptHandle());
                }
                else {
                    failedRequest = new FailedRequest(sqsMessage.getReceiptHandle());
                }
                failedRequestList.add(failedRequest);
            } catch (RuntimeException e) {
                // Log runtime exceptions and add the message to the retry list with a new FailedRequest
                LOGGER.error("Exception occurred processing message id {} because of exception: {}. Will retry.",
                        sqsMessage.getMessageId(), e.getMessage(), e);
                failedRequestList.add(new FailedRequest(sqsMessage.getReceiptHandle()));
            }
        });

        processFailedRequests(failedRequestList);
        return new SQSBatchResponse(batchItemFailures);
    }

    private void processFailedRequests(List<FailedRequest> failedRequestList) {
        failedRequestList.forEach(this::processFailedRequest);
    }

    private void processFailedRequest(FailedRequest failedRequest) {
        int visibilityTimeout = failedRequest.getRetryAfter() > 0 ? failedRequest.getRetryAfter() : calculateVisibilityTimeout();
        setVisibilityTimeout(failedRequest.getReceiptHandle(), visibilityTimeout);
    }

    private void setVisibilityTimeout(String receiptHandle, int visibilityTimeout) {
        ChangeMessageVisibilityRequest changeMessageVisibilityRequest = ChangeMessageVisibilityRequest.builder()
                .queueUrl(QUEUE_URL)
                .receiptHandle(receiptHandle)
                .visibilityTimeout(visibilityTimeout)
                .build();

        sqsClient.changeMessageVisibility(changeMessageVisibilityRequest);
    }

    private int calculateVisibilityTimeout() {
        return new Random().nextInt((maxValue - minValue) + 1) + minValue;
    }

    private static void setupEnvVariables() {
        minValue = getEnvVarAsInt("MIN_VALUE", DEFAULT_MIN_VALUE);
        maxValue = getEnvVarAsInt("MAX_VALUE", DEFAULT_MAX_VALUE);

        if (minValue > maxValue) {
            LOGGER.error("MIN_VALUE is greater than MAX_VALUE, adjusting to default values");
            minValue = DEFAULT_MIN_VALUE;
            maxValue = DEFAULT_MAX_VALUE;
        }

        final String levelString = System.getenv("LOG_LEVEL");
        if (levelString != null) {
            System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, levelString);
        }
    }

    private static int getEnvVarAsInt(String varName, int defaultValue) {
        try {
            String value = System.getenv(varName);
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid environment variable for {}: {}. Using default value: {}", varName, System.getenv(varName), defaultValue);
            return defaultValue;
        }
    }

    private MessageProcessor getProcessor() {
        return messageProcessor;
    }

    public void setMessageProcessor(final MessageProcessor messageProcessor) {
        this.messageProcessor = messageProcessor;
    }
}
