package com.sailthru.sqs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.sailthru.sqs.exception.RetryLaterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class SQSLambdaHandlerTest {
    private SQSLambdaHandler testInstance = new SQSLambdaHandler();

    @Mock
    private Context mockContext;

    @Mock
    private SQSEvent mockSQSEvent;

    @Mock
    private MessageProcessor mockMessageProcessor;

    @Mock
    private SQSEvent.SQSMessage mockSQSMessage;

    @BeforeEach
    void setUp() {
        testInstance.setMessageProcessor(mockMessageProcessor);

        when(mockSQSEvent.getRecords()).thenReturn(List.of(mockSQSMessage));
    }

    @Test
    void handleRequestShouldAddToBatchItemRequestFailureForRetryLaterException() throws Exception {
        givenExceptionThrown(RetryLaterException.class);

        final SQSBatchResponse response = testInstance.handleRequest(mockSQSEvent, mockContext);

        assertThat(response.getBatchItemFailures().size(), is(1));
    }

    private void givenExceptionThrown(final Class<? extends Exception> exceptionClass) throws Exception {
        doThrow(exceptionClass).when(mockMessageProcessor).process(mockSQSMessage);
    }
}
