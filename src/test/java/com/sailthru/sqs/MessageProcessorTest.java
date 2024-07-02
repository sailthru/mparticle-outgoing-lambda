package com.sailthru.sqs;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
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
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageProcessorTest {
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
    void givenNoAuthenticationKeyProvidedThenIllegalArgumentExceptionShouldBeThrown() throws Exception  {
        givenMessageWithoutAuthenticationKey();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> testInstance.process(mockSQSMessage));
        assertEquals("Authentication key not provided.", exception.getMessage());
    }

    @Test
    void givenNoAuthenticationSecretProvidedThenIllegalArgumentExceptionShouldBeThrown() throws Exception  {
        givenMessageWithoutAuthenticationSecret();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> testInstance.process(mockSQSMessage));
        assertEquals("Authentication secret not provided.", exception.getMessage());
    }

    @Test
    void givenValidPayloadProvidedThenMessageSubmitted() throws Exception {
        givenValidMessage();

        testInstance.process(mockSQSMessage);

        verify(mockMParticleClient).submit(messageCaptor.capture());

        final MParticleMessage message = messageCaptor.getValue();
        assertThat(message.getAuthenticationKey(), is(equalTo("AuthKey123")));
        assertThat(message.getAuthenticationSecret(), is(equalTo("AuthSecret123")));
        assertThat(message.getEventName(), is(equalTo("email_open")));
        assertThat(message.getEventType(), is(equalTo("OTHER")));
    }

    private void givenValidMessage() throws Exception {
        final String json = loadResourceFileContent("/messages/valid.json");
        when(mockSQSMessage.getBody()).thenReturn(json);
    }

    private String loadResourceFileContent(final String path) throws IOException, URISyntaxException {
        return Files.readString(Paths.get(getClass().getResource(path).toURI()));
    }

    private void givenMessageWithoutAuthenticationSecret() throws Exception {
        final String json = loadResourceFileContent("/messages/invalid2.json");
        when(mockSQSMessage.getBody()).thenReturn(json);
    }

    private void givenMessageWithoutAuthenticationKey() throws Exception {
        final String json = loadResourceFileContent("/messages/invalid1.json");
        when(mockSQSMessage.getBody()).thenReturn(json);
    }
}
