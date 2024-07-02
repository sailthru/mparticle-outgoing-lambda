package com.sailthru.sqs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.sailthru.sqs.exception.RetryLaterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.util.ArrayList;
import java.util.List;

public class SQSLambdaHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SQSLambdaHandler.class);

    private MessageProcessor messageProcessor;
    private SqsClient sqsClient;

    @Override
    public SQSBatchResponse handleRequest(final SQSEvent event, final Context context) {
        final List<SQSBatchResponse.BatchItemFailure> batchItemFailures = new ArrayList<>();

        event.getRecords().forEach(sqsMessage -> {
            try {
                getProcessor().process(sqsMessage);
            } catch (RetryLaterException e) {
                LOGGER.error("Exception occurred processing message id {} because of exception: {}.  Will retry.", sqsMessage.getMessageId(), e.getMessage(), e);
                batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(sqsMessage.getMessageId()));
            }
        });

        return new SQSBatchResponse(batchItemFailures);
    }

    private MessageProcessor getProcessor() {
        if(messageProcessor == null) {
            messageProcessor = new MessageProcessor();
        }

        return messageProcessor;
    }

    public void setMessageProcessor(final MessageProcessor messageProcessor) {
        this.messageProcessor = messageProcessor;
    }
}
