package com.sailthru.sqs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.sailthru.sqs.exception.NoRetryException;
import com.sailthru.sqs.exception.RetryLaterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityResponse;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private SqsClient mockSqsClient;

    private SQSLambdaHandler testInstance;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        testInstance = new SQSLambdaHandler(mockSqsClient);
        testInstance.setMessageProcessor(mockMessageProcessor);
        SQSLambdaHandler.QUEUE_URL = "testURL";
        lenient().when(mockSQSEvent.getRecords()).thenReturn(List.of(mockSQSMessage));
    }

    @Test
    public void givenMessageProcessSuccessfully_thenReturnEmptyFailureItemsLists() throws Exception {
        SQSEvent sqsEvent = createSQSEvent("messageId1", "body1", "receiptHandle1");

        doNothing().when(mockMessageProcessor).process(any());

        SQSBatchResponse response = testInstance.handleRequest(sqsEvent, mockContext);

        assertTrue(response.getBatchItemFailures().isEmpty());
        verify(mockMessageProcessor, times(1)).process(any());
    }

    @Test
    void givenHandleRequestThrowsNoRetryException_thenReturnEmptyFailureItemsList() throws Exception {

        SQSEvent sqsEvent = createSQSEvent("messageId2", "body2", "receiptHandle2");

        doThrow(new NoRetryException("No retry exception")).when(mockMessageProcessor).process(any());

        SQSBatchResponse response = testInstance.handleRequest(sqsEvent, mockContext);

        verify(mockMessageProcessor, times(1)).process(any());
        assertTrue(response.getBatchItemFailures().isEmpty());
        verify(mockSqsClient, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
    }

    @Test
    void givenHandleRequestThrowsRetryException_thenReturnFailureItemListAndVisibilityTimeoutIsSet() throws Exception {

        SQSEvent sqsEvent = createSQSEvent("messageId3", "body3", "receiptHandle3");

        doThrow(new RetryLaterException(500, 120)).when(mockMessageProcessor).process(any());

        SQSBatchResponse response = testInstance.handleRequest(sqsEvent, mockContext);

        assertThat(response.getBatchItemFailures().size(), is(1));
        assertEquals("messageId3", response.getBatchItemFailures().getFirst().getItemIdentifier());

        assertEquals(1, response.getBatchItemFailures().size());
        verify(mockMessageProcessor, times(1)).process(any());
        verify(mockSqsClient, times(1)).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
    }

    @Test
    public void testSetVisibilityTimeout_CalledWithCorrectParameters() throws Exception {
        SQSEvent sqsEvent = createSQSEvent("messageId6", "body6", "receiptHandle6");

        doThrow(new RetryLaterException(500, 120)).when(mockMessageProcessor).process(any());

        testInstance.handleRequest(sqsEvent, mockContext);

        ArgumentCaptor<ChangeMessageVisibilityRequest> argumentCaptor =
                ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
        verify(mockSqsClient, times(1)).changeMessageVisibility(argumentCaptor.capture());

        ChangeMessageVisibilityRequest capturedArgument = argumentCaptor.getValue();
        assertEquals("receiptHandle6", capturedArgument.receiptHandle());
        assertEquals(120, capturedArgument.visibilityTimeout());
    }

    @Test
    public void givenCalculatingVisibilityTimeout_thenReturnWithinExpectedBounds() {
        int receiveCount = 3;
        int visibilityTimeout = testInstance.calculateVisibilityTimeout(receiveCount);

        int lowerBound = (int) (180 * Math.pow(2, receiveCount - 1));
        int higherBound = (int) (180 * Math.pow(2, receiveCount));

        assertTrue(visibilityTimeout >= lowerBound && visibilityTimeout <= higherBound);
    }

    @Test
    void givenMessageProcessSuccessfully_thenVisibilityTimeoutIsNotSet() throws Exception {
        final int recordSize = 7;
        SQSEvent sqsEvent = new SQSEvent();
        sqsEvent.setRecords(getSqsMessages(recordSize));

        doNothing().when(mockMessageProcessor).process(any(SQSEvent.SQSMessage.class));

        Context context = mock(Context.class);
        SQSBatchResponse response = testInstance.handleRequest(sqsEvent, context);
        assertNotNull(response);
        verify(mockMessageProcessor, times(recordSize)).process(any(SQSEvent.SQSMessage.class));
        verify(mockSqsClient, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
        assertTrue(response.getBatchItemFailures().isEmpty());
    }

    @Test
    void testWhenThrownRetryException_thenVisibilityTimoutIsSet() throws Exception {
        final int recordSize = 10;
        SQSEvent sqsEvent = new SQSEvent();
        sqsEvent.setRecords(getSqsMessages(recordSize));

        setupMock(sqsEvent);

        Context context = mock(Context.class);
        SQSBatchResponse response = testInstance.handleRequest(sqsEvent, context);
        assertThat(response.getBatchItemFailures().size(), is(5));
        assertNotNull(response);
        verify(mockMessageProcessor, times(recordSize)).process(any(SQSEvent.SQSMessage.class));
        verify(mockSqsClient, times(5)).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
        assertThat(response.getBatchItemFailures().size(), is(5));
    }

    private void setupMock(SQSEvent sqsEvent) throws NoRetryException, RetryLaterException {
        List<SQSEvent.SQSMessage> records = sqsEvent.getRecords();
        for (int i = 0; i < records.size(); i++) {
            if (i % 2 == 0) {
                doThrow(RetryLaterException.class).when(mockMessageProcessor).process(records.get(i));
            } else {
                doNothing().when(mockMessageProcessor).process(records.get(i));
            }
        }
        when(mockSqsClient.changeMessageVisibility(any(ChangeMessageVisibilityRequest.class)))
                .thenReturn(ChangeMessageVisibilityResponse.builder().build());
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

    private SQSEvent createSQSEvent(String messageId, String body, String receiptHandle) {
        SQSEvent sqsEvent = new SQSEvent();
        SQSEvent.SQSMessage sqsMessage = new SQSEvent.SQSMessage();
        sqsMessage.setMessageId(messageId);
        sqsMessage.setBody(body);
        sqsMessage.setReceiptHandle(receiptHandle);
        sqsEvent.setRecords(Collections.singletonList(sqsMessage));
        return sqsEvent;
    }
}
