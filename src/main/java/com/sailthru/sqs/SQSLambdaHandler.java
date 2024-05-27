package com.sailthru.sqs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SQSLambdaHandler implements RequestHandler<SQSEvent, String> {
    private static final Logger logger = LoggerFactory.getLogger(SQSLambdaHandler.class);

    @Override
    public String handleRequest(SQSEvent event, Context context) {
        event.getRecords().forEach(record -> {
            String message = record.getBody();
            logger.info("Received message: {}", message);
        });
        return "Processed " + event.getRecords().size() + " messages.";
    }
}
