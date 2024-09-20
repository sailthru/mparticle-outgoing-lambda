package com.sailthru.sqs;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.sailthru.sqs.exception.NoRetryException;
import com.sailthru.sqs.exception.PayloadTooLargeException;
import com.sailthru.sqs.exception.RetryLaterException;
import com.sailthru.sqs.message.MParticleEventName;
import com.sailthru.sqs.message.MParticleEventType;
import com.sailthru.sqs.message.MParticleOutgoingMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageProcessorTest {
    private MessageProcessor testInstance = new MessageProcessor(false);

    @Mock
    private SQSEvent.SQSMessage mockSQSMessage;

    @Mock
    private MParticleClient mockMParticleClient;

    @Captor
    private ArgumentCaptor<MParticleOutgoingMessage> messageCaptor;

    @BeforeEach
    void setUp() {
        testInstance.setMParticleClient(mockMParticleClient);
    }

    @Test
    void givenNoAuthenticationKeyProvidedThenCorrectExceptionShouldBeThrown() {
        givenMessageWithoutAuthenticationKey();

        NoRetryException exception = assertThrows(NoRetryException.class,
                () -> testInstance.process(mockSQSMessage));
        assertEquals("Authentication key not provided.", exception.getMessage());
        verifyNoInteractions(mockMParticleClient);
    }

    @Test
    void givenNoAuthenticationSecretProvidedThenCorrectExceptionShouldBeThrown() {
        givenMessageWithoutAuthenticationSecret();

        NoRetryException exception = assertThrows(NoRetryException.class,
                () -> testInstance.process(mockSQSMessage));
        assertEquals("Authentication secret not provided.", exception.getMessage());
        verifyNoInteractions(mockMParticleClient);
    }

    @Test
    void givenValidPayloadProvidedThenMessageSubmitted() throws NoRetryException, RetryLaterException {
        givenValidMessage();

        testInstance.process(mockSQSMessage);

        verify(mockMParticleClient).submit(messageCaptor.capture());

        final MParticleOutgoingMessage message = messageCaptor.getValue();
        assertThat(message.getAuthenticationKey(), is(equalTo("test_key")));
        assertThat(message.getAuthenticationSecret(), is(equalTo("test_secret")));
        assertThat(message.getEvents().size(), is(2));

        final MParticleOutgoingMessage.Event event1 = message.getEvents().get(0);
        assertThat(event1.getEventName(), is(MParticleEventName.EMAIL_SUBSCRIBE));
        assertThat(event1.getEventType(), is(equalTo(MParticleEventType.OTHER)));

        final MParticleOutgoingMessage.Event event2 = message.getEvents().get(1);
        assertThat(event2.getEventName(), is(MParticleEventName.EMAIL_UNSUBSCRIBE));
        assertThat(event2.getEventType(), is(equalTo(MParticleEventType.OTHER)));
    }

    @Test
    void givenTooLargeMesasgeProvidedThenCorrectExceptionShouldBeThrown() {
        givenTooLongMessage();

        PayloadTooLargeException exception = assertThrows(PayloadTooLargeException.class,
            () -> testInstance.process(mockSQSMessage));
        System.out.println(exception.getSize());
        assertThat(exception.getSize(), greaterThan(200_000L));
        verifyNoInteractions(mockMParticleClient);
    }

    private void givenValidMessage() {
        final String json = loadResourceFileContent("/messages/valid.json");
        when(mockSQSMessage.getBody()).thenReturn(json);
    }

    private void givenTooLongMessage() {
        final String json = loadResourceFileContent("/messages/too_long.json");
        when(mockSQSMessage.getBody()).thenReturn(json);
    }

    private String loadResourceFileContent(final String path) {
        try {
            return Files.readString(Paths.get(getClass().getResource(path).toURI()));
        } catch (IOException | URISyntaxException | NullPointerException e) {
            fail("Could not load resource " + path + "from classpath: " + e.getMessage());
            return null;
        }
    }

    private void givenMessageWithoutAuthenticationSecret() {
        final String json = loadResourceFileContent("/messages/invalid2.json");
        when(mockSQSMessage.getBody()).thenReturn(json);
    }

    private void givenMessageWithoutAuthenticationKey() {
        final String json = loadResourceFileContent("/messages/invalid1.json");
        when(mockSQSMessage.getBody()).thenReturn(json);
    }

    private void givenDisabledMParticleSend() {
        testInstance = new MessageProcessor(true);
        testInstance.setMParticleClient(mockMParticleClient);
    }
}
