package com.sailthru.sqs;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.sailthru.sqs.exception.RetryLaterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageProcessorTest {
    public static final String VALID_PAYLOAD = "{\"authenticationKey\":\"AuthKey123\",\"authenticationSecret\":\"AuthSecret123\",\"eventName\":\"email_open\",\"eventType\":\"OTHER\",\"additionalData\":{\"Key1\":\"Value1\",\"Key2\":\"Value2\"},\"profileEmail\":\"john.cooper@sailthru.com\"}";
    public static final String INVALID_PAYLOAD_1 = "{\"authenticationKey\":\"\",\"authenticationSecret\":\"AuthSecret123\",\"eventName\":\"email_open\",\"eventType\":\"OTHER\",\"additionalData\":{\"Key1\":\"Value1\",\"Key2\":\"Value2\"},\"profileEmail\":\"john.cooper@sailthru.com\"}";
    public static final String INVALID_PAYLOAD_2 = "{\"authenticationKey\":\"AuthKey123\",\"authenticationSecret\":\"\",\"eventName\":\"email_open\",\"eventType\":\"OTHER\",\"additionalData\":{\"Key1\":\"Value1\",\"Key2\":\"Value2\"},\"profileEmail\":\"john.cooper@sailthru.com\"}";

    private MessageProcessor testInstance = new MessageProcessor();

    @Mock
    private SQSEvent.SQSMessage mockSQSMessage;

    @Mock
    private MParticleClient mockMParticleClient;

    @Captor
    private ArgumentCaptor<MParticleMessage> messageCaptor;

    @BeforeEach
    void setUp() {
        testInstance.setMParticleClient(mockMParticleClient);
    }

    @Test
    void givenNoAuthenticationKeyProvidedThenIllegalArgumentExceptionShouldBeThrown() {
        givenMessageWithoutAuthenticationKey();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> testInstance.process(mockSQSMessage));
        assertEquals("Authentication key not provided.", exception.getMessage());

    }

    @Test
    void givenNoAuthenticationSecretProvidedThenIllegalArgumentExceptionShouldBeThrown() {
        givenMessageWithoutAuthenticationSecret();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> testInstance.process(mockSQSMessage));
        assertEquals("Authentication secret not provided.", exception.getMessage());

    }

    @Test
    void givenValidPayloadProvidedThenMessageSubmitted() throws RetryLaterException {
        givenValidMessage();

        testInstance.process(mockSQSMessage);

        verify(mockMParticleClient).submit(messageCaptor.capture());

        final MParticleMessage message = messageCaptor.getValue();
        assertThat(message.getAuthenticationKey(), is(equalTo("AuthKey123")));
        assertThat(message.getAuthenticationSecret(), is(equalTo("AuthSecret123")));
        assertThat(message.getEventName(), is(equalTo("email_open")));
        assertThat(message.getEventType(), is(equalTo("OTHER")));
    }

    private void givenValidMessage() {
        when(mockSQSMessage.getBody()).thenReturn(VALID_PAYLOAD);
    }

    private void givenMessageWithoutAuthenticationSecret() {
        when(mockSQSMessage.getBody()).thenReturn(INVALID_PAYLOAD_2);
    }

    private void givenMessageWithoutAuthenticationKey() {
        when(mockSQSMessage.getBody()).thenReturn(INVALID_PAYLOAD_1);
    }
}
