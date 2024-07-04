package com.sailthru.sqs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.sailthru.sqs.exception.NoRetryException;
import com.sailthru.sqs.exception.RetryLaterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SQSLambdaHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {
    static {
        setupLogLevel();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SQSLambdaHandler.class);

    private MessageProcessor messageProcessor;

    public SQSLambdaHandler() {
        messageProcessor = new MessageProcessor();
    }

    @Override
    public SQSBatchResponse handleRequest(final SQSEvent event, final Context context) {
        setupLogLevel();

        final List<SQSBatchResponse.BatchItemFailure> batchItemFailures = new ArrayList<>();

        event.getRecords().forEach(sqsMessage -> {
            try {
                getProcessor().process(sqsMessage);
            } catch (NoRetryException e) {
                LOGGER.error("Non retryable exception occurred processing message id {} because of: {}.",
                        sqsMessage.getMessageId(),
                        e.getMessage(), e);
            } catch (RetryLaterException | RuntimeException e) {
                LOGGER.error("Exception occurred processing message id {} because of exception: {}.  Will retry.",
                        sqsMessage.getMessageId(),
                        e.getMessage(), e);

                batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(sqsMessage.getMessageId()));
            }
        });

        return new SQSBatchResponse(batchItemFailures);
    }

    private static void setupLogLevel() {
        final String levelString = System.getenv("LOG_LEVEL");
        if (levelString != null) {
            System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, levelString);
        }
    }

    private MessageProcessor getProcessor() {
        return messageProcessor;
    }

    public void setMessageProcessor(final MessageProcessor messageProcessor) {
        this.messageProcessor = messageProcessor;
    }
}
