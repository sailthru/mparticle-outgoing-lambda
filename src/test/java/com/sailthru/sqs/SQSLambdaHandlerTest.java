package com.sailthru.sqs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.sailthru.sqs.exception.NoRetryException;
import com.sailthru.sqs.exception.RetryLaterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SQSLambdaHandlerTest {

    @Mock
    private Context mockContext;

    @Mock
    private SQSEvent mockSQSEvent;

    @Mock
    private MessageProcessor mockMessageProcessor;

    @Mock
    private SQSEvent.SQSMessage mockSQSMessage;

    @Mock
    private SqsClient sqsClient;

    @InjectMocks
    private SQSLambdaHandler testInstance;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        testInstance = new SQSLambdaHandler(sqsClient);
        testInstance.setMessageProcessor(mockMessageProcessor);

        lenient().when(mockSQSEvent.getRecords()).thenReturn(List.of(mockSQSMessage));
    }

    @Test
    void handleRequestShouldAddToBatchItemRequestFailureForRetryLaterException() throws Exception {
        String queueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue";

        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl(queueUrl).build());
        givenExceptionThrown(RetryLaterException.class);

        final SQSBatchResponse response = testInstance.handleRequest(mockSQSEvent, mockContext);
        assertThat(response.getBatchItemFailures().size(), is(1));
    }

    @Test
    void testGetQueueUrl() {
        String queueName = "test-queue";
        String queueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue";

        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl(queueUrl).build());

        String result = testInstance.getQueueUrl(queueName);

        assertNotNull(result);
        verify(sqsClient, times(1)).getQueueUrl(any(GetQueueUrlRequest.class));
    }

    @Test
    void testWhenProcessMessageThenChangeMessageVisibilityIsNotCalled() throws Exception {
        final int recordSize = 7;
        SQSEvent sqsEvent = new SQSEvent();
        sqsEvent.setRecords(getSqsMessages(recordSize));

        String queueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue";

        doNothing().when(mockMessageProcessor).process(any(SQSEvent.SQSMessage.class));
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl(queueUrl).build());

        Context context = mock(Context.class);
        SQSBatchResponse response = testInstance.handleRequest(sqsEvent, context);
        assertNotNull(response);
        verify(mockMessageProcessor, times(recordSize)).process(any(SQSEvent.SQSMessage.class));
        verify(sqsClient, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
        assertThat(response.getBatchItemFailures().size(), is(0));
    }

    @Test
    void testWhenThrownRetryExceptionThenChangeMessageVisibilityCalled() throws Exception {
        final int recordSize = 10;
        SQSEvent sqsEvent = new SQSEvent();
        sqsEvent.setRecords(getSqsMessages(recordSize));

        String queueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue";
        setupMock(sqsEvent, queueUrl);

        Context context = mock(Context.class);
        SQSBatchResponse response = testInstance.handleRequest(sqsEvent, context);
        assertThat(response.getBatchItemFailures().size(), is(5));
        assertNotNull(response);
        verify(mockMessageProcessor, times(recordSize)).process(any(SQSEvent.SQSMessage.class));
        verify(sqsClient, times(5)).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
        assertThat(response.getBatchItemFailures().size(), is(5));
    }

    private void setupMock(SQSEvent sqsEvent, String queueUrl) throws NoRetryException, RetryLaterException {
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl(queueUrl).build());

        List<SQSEvent.SQSMessage> records = sqsEvent.getRecords();
        for (int i = 0; i < records.size(); i++) {
            if (i % 2 == 0) {
                doThrow(RetryLaterException.class).when(mockMessageProcessor).process(records.get(i));
            } else {
                doNothing().when(mockMessageProcessor).process(records.get(i));
            }
        }
        when(sqsClient.changeMessageVisibility(any(ChangeMessageVisibilityRequest.class)))
                .thenReturn(ChangeMessageVisibilityResponse.builder().build());
    }

    private void givenExceptionThrown(final Class<? extends Exception> exceptionClass) throws Exception {
        doThrow(exceptionClass).when(mockMessageProcessor).process(mockSQSMessage);
    }

    private static List<SQSEvent.SQSMessage> getSqsMessages(int size) {
        return IntStream.range(0, size).mapToObj(SQSLambdaHandlerTest::generateSqsMessage).collect(Collectors.toList());
    }

    private static SQSEvent.SQSMessage generateSqsMessage(int index) {
        SQSEvent.SQSMessage sqsMessage = new SQSEvent.SQSMessage();
        sqsMessage.setBody("test message" + index);
        sqsMessage.setReceiptHandle("test-receipt-handle" + index);
        return sqsMessage;
    }
}
