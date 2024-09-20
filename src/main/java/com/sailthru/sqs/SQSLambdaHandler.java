package com.sailthru.sqs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.sailthru.sqs.exception.NoRetryException;
import com.sailthru.sqs.exception.PayloadTooLargeException;
import com.sailthru.sqs.exception.RetryLaterException;
import com.sailthru.sqs.metrics.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.simple.SimpleLogger;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SQSLambdaHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {
    static final String SQS_URL_KEY = "SQS_URL";
    static final String BASE_TIMEOUT_KEY = "BASE_TIMEOUT";
    static final String TIMEOUT_FACTOR_KEY = "TIMEOUT_FACTOR";
    static final String MPARTICLE_DISABLED_KEY = "MPARTICLE_DISABLED";
    private static Logger LOGGER;

    private String queueUrl;
    private int baseTimeout;
    private int timeoutFactor;
    private boolean mparticleDisabled = false;

    private final SqsClient sqsClient;
    private MessageProcessor messageProcessor;
    private Metrics metrics;

    static {
        initializeLogger();
    }

    public SQSLambdaHandler() {
        this(System.getenv(), SqsClient.builder().region(Region.US_EAST_1).build());
    }

    // @VisibleForTesting
    SQSLambdaHandler(Map<String, String> env, SqsClient sqsClient) {
        initializeSystemVars(env);
        this.messageProcessor = new MessageProcessor(mparticleDisabled);
        this.sqsClient = sqsClient;
        this.metrics = new Metrics();
    }

    @Override
    public SQSBatchResponse handleRequest(final SQSEvent event, final Context context) {
        final List<SQSBatchResponse.BatchItemFailure> batchItemFailures = new ArrayList<>();
        final List<FailedRequest> changeVisibilityList = new ArrayList<>();

        event.getRecords().forEach(sqsMessage -> {
            try {
                getMessageProcessor().process(sqsMessage);
            } catch (NoRetryException e) {
                LOGGER.error(
                    "Non-retryable exception occurred processing message id {} because of: [{}] {}.",
                        sqsMessage.getMessageId(),
                        e.getStatusCode(),
                        e.getMessage(), e);
            } catch (PayloadTooLargeException e) {
                // only log the first time we receive the message
                if (getApproximateReceiveCount(sqsMessage) <= 1) {
                    LOGGER.error("Message {} is too large ({} bytes), will not send to mParticle. [{}] {}",
                        sqsMessage.getMessageId(),
                        e.getMessage(),
                        e.getOriginalMessage().getClientId(),
                        e.getOriginalMessage().getProfileMpId() != null ?
                            e.getOriginalMessage().getProfileMpId() :
                            e.getOriginalMessage().getProfileEmail());
                }

                // this will over-evaluate the metric but that's better than missing it sometimes
                metrics.mark(context, sqsMessage, "MessageTooLarge", 1);
                // don't add to the change visibility list, as we won't adjust the visiblity timeout
                // we'll retry those messages immediately
                batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(sqsMessage.getMessageId()));
            } catch (RetryLaterException e) {
                LOGGER.warn(
                    "Retryable exception occurred processing message id {} because of exception: [{}] {}. Will retry.",
                    sqsMessage.getMessageId(),
                    e.getStatusCode(),
                    e.getMessage(),
                    e
                );
                FailedRequest failedRequest = new FailedRequest(sqsMessage.getMessageId(), e.getStatusCode(),
                        e.getRetryAfter(), sqsMessage.getReceiptHandle(), getApproximateReceiveCount(sqsMessage));

                changeVisibilityList.add(failedRequest);
                batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(sqsMessage.getMessageId()));
            }
        });

        changeVisibilityForFailedRequests(changeVisibilityList);
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

    private void changeVisibilityForFailedRequests(List<FailedRequest> failedRequestList) {
        failedRequestList.forEach(this::processFailedRequest);
    }

    private void processFailedRequest(FailedRequest failedRequest) {
        try {
            final int visibilityTimeout = (int) Math.min(
                failedRequest.getRetryAfter() > 0 ?
                    failedRequest.getRetryAfter() :
                    calculateVisibilityTimeout(failedRequest.getReceiveCount()),
                Integer.MAX_VALUE);
            setVisibilityTimeout(failedRequest.getReceiptHandle(), visibilityTimeout);
        } catch (RuntimeException e) {
            LOGGER.debug("Change visibility timeout error: {}", e.getMessage(), e);
        }
    }

    void setVisibilityTimeout(String receiptHandle, int visibilityTimeout) {
        ChangeMessageVisibilityRequest changeMessageVisibilityRequest = ChangeMessageVisibilityRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .visibilityTimeout(visibilityTimeout)
                .build();

        sqsClient.changeMessageVisibility(changeMessageVisibilityRequest);
    }

    int calculateVisibilityTimeout(int receiveCount) {
        final double lowerBound = baseTimeout * Math.pow(timeoutFactor, receiveCount - 1);
        final double higherBound = baseTimeout * Math.pow(timeoutFactor, receiveCount);
        return (int) (Math.random() * (higherBound - lowerBound)) + (int) lowerBound;
    }

    static void initializeLogger() {
        final String levelString = System.getenv("LOG_LEVEL");
        if (levelString != null) {
            System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, levelString);
        }
        LOGGER = LoggerFactory.getLogger(SQSLambdaHandler.class);
    }

    // @VisibleForTesting
    MessageProcessor getMessageProcessor() {
        return messageProcessor;
    }

    // @VisibleForTesting
    void setMessageProcessor(final MessageProcessor messageProcessor) {
        this.messageProcessor = messageProcessor;
    }

    private void initializeSystemVars(Map<String, String> env) {
        final int DEFAULT_BASE_TIMEOUT = 180;
        final int DEFAULT_TIMEOUT_FACTOR = 2;

        queueUrl = env.get(SQS_URL_KEY);
        if (queueUrl == null || queueUrl.isEmpty()) {
            LOGGER.error("QUEUE URL is not set");
            throw new IllegalArgumentException("QUEUE URL is not set");
        }

        baseTimeout = getEnvVarAsInt(env, BASE_TIMEOUT_KEY, DEFAULT_BASE_TIMEOUT);
        timeoutFactor = getEnvVarAsInt(env, TIMEOUT_FACTOR_KEY, DEFAULT_TIMEOUT_FACTOR);

        if (DEFAULT_TIMEOUT_FACTOR > timeoutFactor) {
            LOGGER.warn("TIMEOUT_FACTOR is less than BASE_TIMEOUT, adjusting to default values");
            timeoutFactor = DEFAULT_TIMEOUT_FACTOR;
        }

        mparticleDisabled = getEnvVarAsInt(env, MPARTICLE_DISABLED_KEY, 0) != 0;
        if (mparticleDisabled) {
            LOGGER.warn("MPARTICLE_DISABLED is set, NO MESSAGES WILL BE SENT TO mPARTICLE!");
        }
    }

    private static int getEnvVarAsInt(Map<String, String> env, String varName, int defaultValue) {
        final String value = env.get(varName);
        try {
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid environment variable for {}: {}. Using default value: {}",
                    varName, value, defaultValue);
            return defaultValue;
        }
    }
}
