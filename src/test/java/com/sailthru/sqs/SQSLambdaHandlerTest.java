package com.sailthru.sqs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.sailthru.sqs.exception.NoRetryException;
import com.sailthru.sqs.exception.PayloadTooLargeException;
import com.sailthru.sqs.exception.RetryLaterException;
import com.sailthru.sqs.message.MParticleOutgoingMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.sailthru.sqs.SQSLambdaHandler.BASE_TIMEOUT_KEY;
import static com.sailthru.sqs.SQSLambdaHandler.MPARTICLE_DISABLED_KEY;
import static com.sailthru.sqs.SQSLambdaHandler.SQS_URL_KEY;
import static com.sailthru.sqs.SQSLambdaHandler.TIMEOUT_FACTOR_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class SQSLambdaHandlerTest {

    @Mock
    private Context mockContext;

    @Mock
    private SQSEvent mockSQSEvent;

    @Mock
    private MessageProcessor mockMessageProcessor;

    @Mock
    private SQSEvent.SQSMessage mockSQSMessage;

    @Mock(answer = Answers.RETURNS_MOCKS)
    private SqsClient mockSqsClient;

    @Mock
    private MParticleClient mockMParticleClient;

    private SQSLambdaHandler testInstance;

    @BeforeEach
    void setUp() {
        final Map<String, String> defaultEnvironment = Map.of(
            SQS_URL_KEY, "test_url",
            BASE_TIMEOUT_KEY, "180",
            TIMEOUT_FACTOR_KEY, "2",
            MPARTICLE_DISABLED_KEY, "0"
        );
        testInstance = new SQSLambdaHandler(defaultEnvironment, mockSqsClient);
        testInstance.setMessageProcessor(mockMessageProcessor);
        lenient().when(mockSQSEvent.getRecords()).thenReturn(List.of(mockSQSMessage));
    }

    @Test
    public void givenMessageProcessSuccessfully_thenReturnEmptyFailureItemsLists() throws Exception {
        SQSEvent sqsEvent = createSQSEvent("messageId1", "body1", "receiptHandle1");

        SQSBatchResponse response = testInstance.handleRequest(sqsEvent, mockContext);

        assertThat(response.getBatchItemFailures(), empty());
        verify(mockMessageProcessor, times(1)).process(any());
    }

    @Test
    void givenHandleRequestThrowsNoRetryException_thenReturnEmptyFailureItemsList() throws Exception {
        SQSEvent sqsEvent = createSQSEvent("messageId2", "body2", "receiptHandle2");
        doThrow(new NoRetryException("No retry exception")).when(mockMessageProcessor).process(any());

        SQSBatchResponse response = testInstance.handleRequest(sqsEvent, mockContext);

        verify(mockMessageProcessor, times(1)).process(any());
        assertThat(response.getBatchItemFailures(), empty());
        verify(mockSqsClient, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
    }

    @Test
    void givenHandleRequestThrowsRetryException_thenReturnFailureItemListAndVisibilityTimeoutIsSet() throws Exception {
        SQSEvent sqsEvent = createSQSEvent("messageId3", "body3", "receiptHandle3");
        doThrow(new RetryLaterException(500, "Internal Server Error", 120)).when(mockMessageProcessor).process(any());

        SQSBatchResponse response = testInstance.handleRequest(sqsEvent, mockContext);

        assertThat(response.getBatchItemFailures(), hasSize(1));
        assertThat(response.getBatchItemFailures().getFirst().getItemIdentifier(), is("messageId3"));

        assertThat(response.getBatchItemFailures(), hasSize(1));
        verify(mockMessageProcessor, times(1)).process(any());
        verify(mockSqsClient, times(1)).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
    }

    @Test
    public void givenInternalServerError_thenSetVisibilityTimeoutCalledWithTimeoutSentInException() throws Exception {
        SQSEvent sqsEvent = createSQSEvent("messageId6", "body6", "receiptHandle6");
        doThrow(new RetryLaterException(500, "Internal Server Error", 120)).when(mockMessageProcessor).process(any());

        testInstance.handleRequest(sqsEvent, mockContext);

        ArgumentCaptor<ChangeMessageVisibilityRequest> argumentCaptor =
                ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
        verify(mockSqsClient, times(1)).changeMessageVisibility(argumentCaptor.capture());

        ChangeMessageVisibilityRequest capturedArgument = argumentCaptor.getValue();
        assertThat(capturedArgument.receiptHandle(), is("receiptHandle6"));
        assertThat(capturedArgument.visibilityTimeout(), is(120));
    }

    @Test
    public void givenCalculatingVisibilityTimeout_thenReturnWithinExpectedBounds() {
        int receiveCount = 3;
        int visibilityTimeout = testInstance.calculateVisibilityTimeout(receiveCount);

        int lowerBound = (int) (180 * Math.pow(2, receiveCount - 1));
        int higherBound = (int) (180 * Math.pow(2, receiveCount));

        assertThat(visibilityTimeout, allOf(greaterThanOrEqualTo(lowerBound), lessThanOrEqualTo(higherBound)));
    }

    @Test
    void givenMessageProcessSuccessfully_thenVisibilityTimeoutIsNotSet() throws Exception {
        final int recordSize = 7;
        SQSEvent sqsEvent = new SQSEvent();
        sqsEvent.setRecords(getSqsMessages(recordSize));

        SQSBatchResponse response = testInstance.handleRequest(sqsEvent, mockContext);
        assertThat(response, notNullValue());
        assertThat(response.getBatchItemFailures(), empty());
        verify(mockMessageProcessor, times(recordSize)).process(any(SQSEvent.SQSMessage.class));
        verify(mockSqsClient, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
    }

    @Test
    void givenRetryRequested_thenVisibilityTimeoutIsSet() throws Exception {
        final int recordSize = 10;
        final int expectedFailures = recordSize / 2;
        SQSEvent sqsEvent = new SQSEvent();
        sqsEvent.setRecords(getSqsMessages(recordSize));
        givenAlterativelyFailingRecords(sqsEvent);

        SQSBatchResponse response = testInstance.handleRequest(sqsEvent, mockContext);

        assertThat(response, notNullValue());
        assertThat(response.getBatchItemFailures(), hasSize(5));
        verify(mockMessageProcessor, times(recordSize)).process(any(SQSEvent.SQSMessage.class));
        verify(mockSqsClient, times(expectedFailures)).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
        assertThat(response.getBatchItemFailures(), hasSize(5));
    }

    @Test
    void givenQueueUrlIsNotSet_thenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class, () -> testInstance = new SQSLambdaHandler());
    }

    @Test
    void givenDisabledMParticleClient_thenNoInteractonWithMParticleOrSqsOccurs() throws Exception {
        givenDisabledMParticleClient();
        var event = createSQSEvent("messageId7", "body7", "receiptHandle7");

        SQSBatchResponse response = testInstance.handleRequest(event, mockContext);

        assertThat(response, notNullValue());
        assertThat(response.getBatchItemFailures(), empty());
        verifyNoInteractions(mockMParticleClient, mockSqsClient);
    }

    @Test
    void givenDisabledMParticleClientAndClientErrors_thenOnlyInteractsWithSqs() throws Exception {
        givenDisabledMParticleClient();
        final int recordSize = 10;
        var sqsEvent = new SQSEvent();
        sqsEvent.setRecords(getSqsMessages(recordSize));
        lenient().doThrow(RetryLaterException.class).when(mockMParticleClient).submit(any());

        SQSBatchResponse response = testInstance.handleRequest(sqsEvent, mockContext);

        assertThat(response, notNullValue());
        assertThat(response.getBatchItemFailures(), empty());
        verifyNoInteractions(mockSqsClient, mockMParticleClient);
    }

    @Test
    void givenPreParseErrors_thenMarkedAsFailureWithoutVisibilityChange() throws Exception {
        final int recordSize = 10;
        var sqsEvent = new SQSEvent();
        sqsEvent.setRecords(getSqsMessages(recordSize));
        doThrow(new PayloadTooLargeException(42, new MParticleOutgoingMessage()))
            .when(mockMessageProcessor).process(any());

        SQSBatchResponse response = testInstance.handleRequest(sqsEvent, mockContext);

        assertThat(response, notNullValue());
        assertThat(response.getBatchItemFailures(), hasSize(recordSize));
        verifyNoInteractions(mockSqsClient, mockMParticleClient);
    }

    @Test
    void givenDisabledMParticleClientAndPreParseErrors_thenMarkedAsFailureWithoutVisibilityChange() throws Exception {
        givenDisabledMParticleClient();
        final int recordSize = 10;
        var sqsEvent = new SQSEvent();
        sqsEvent.setRecords(getSqsMessages(recordSize));
        doThrow(new PayloadTooLargeException(42, new MParticleOutgoingMessage()))
            .when(mockMessageProcessor).process(any());
        testInstance.setMessageProcessor(mockMessageProcessor);

        SQSBatchResponse response = testInstance.handleRequest(sqsEvent, mockContext);

        assertThat(response, notNullValue());
        assertThat(response.getBatchItemFailures(), hasSize(recordSize));
        verifyNoInteractions(mockSqsClient, mockMParticleClient);
    }

    private void givenAlterativelyFailingRecords(SQSEvent sqsEvent) throws NoRetryException, RetryLaterException {
        List<SQSEvent.SQSMessage> records = sqsEvent.getRecords();
        for (int i = 0; i < records.size(); i++) {
            if (i % 2 == 0) {
                doThrow(RetryLaterException.class).when(mockMessageProcessor).process(records.get(i));
            } else {
                doNothing().when(mockMessageProcessor).process(records.get(i));
            }
        }
    }

    private void givenDisabledMParticleClient() {
        final Map<String, String> defaultEnvironment = Map.of(
            SQS_URL_KEY, "test_url",
            BASE_TIMEOUT_KEY, "180",
            TIMEOUT_FACTOR_KEY, "2",
            MPARTICLE_DISABLED_KEY, "1"
        );
        testInstance = new SQSLambdaHandler(defaultEnvironment, mockSqsClient);
        testInstance.getMessageProcessor().setMParticleClient(mockMParticleClient);
    }

    private static List<SQSEvent.SQSMessage> getSqsMessages(int size) {
        return IntStream.range(0, size).mapToObj(SQSLambdaHandlerTest::generateSqsMessage).collect(Collectors.toList());
    }

    private static SQSEvent.SQSMessage generateSqsMessage(int index) {
        SQSEvent.SQSMessage sqsMessage = new SQSEvent.SQSMessage();
        sqsMessage.setBody("{\"authenticationKey\":\"1\",\"authenticationSecret\":\"2\",\"message\":\"test message" + index + "\"}");
        sqsMessage.setReceiptHandle("test-receipt-handle" + index);
        sqsMessage.setMessageId(UUID.randomUUID().toString());
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
