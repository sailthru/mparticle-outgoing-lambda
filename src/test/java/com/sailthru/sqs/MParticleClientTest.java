package com.sailthru.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mparticle.client.EventsApi;
import com.mparticle.model.Batch;
import com.mparticle.model.CustomEvent;
import com.mparticle.model.CustomEventData;
import com.mparticle.model.UserIdentities;
import com.sailthru.sqs.exception.NoRetryException;
import com.sailthru.sqs.exception.RetryLaterException;
import com.sailthru.sqs.message.MParticleEventName;
import com.sailthru.sqs.message.MParticleOutgoingMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static com.mparticle.model.CustomEvent.EventTypeEnum.CUSTOM_EVENT;
import static com.sailthru.sqs.MParticleClient.DEFAULT_BASE_URL;
import static com.sailthru.sqs.MParticleClient.TOO_MANY_REQUESTS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MParticleClientTest {
    private MParticleClient testInstance;

    @Mock
    private ApiFactory mockApiFactory;

    @Mock
    private EventsApi mockEventsApi;

    @Captor
    private ArgumentCaptor<Batch> batchCaptor;

    @Mock
    private Call<Void> mockCall;

    @Mock
    private Response<Void> mockResponse;

    @BeforeEach
    void setUp() throws Exception {
        when(mockApiFactory.create(anyString(), anyString(), anyString())).thenReturn(mockEventsApi);
        when(mockEventsApi.uploadEvents(any(Batch.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(mockResponse);
        testInstance = new MParticleClient(mockApiFactory);
    }

    @Test
    void givenNon429UnsuccessfulResponseThenNoRetryExceptionIsThrown() {
        final MParticleOutgoingMessage validMessageWithURL = givenValidMessage("/messages/valid.json");

        when(mockResponse.isSuccessful()).thenReturn(false);

        assertThrows(NoRetryException.class, () -> testInstance.submit(validMessageWithURL));
    }

    @Test
    void given429UnsuccessfulResponseThenRetryExceptionIsThrown() {
        final MParticleOutgoingMessage validMessageWithURL = givenValidMessage("/messages/valid.json");

        when(mockResponse.isSuccessful()).thenReturn(false);
        when(mockResponse.code()).thenReturn(TOO_MANY_REQUESTS);

        assertThrows(RetryLaterException.class, () -> testInstance.submit(validMessageWithURL));
    }

    @Test
    void given500UnsuccessfulResponseThenRetryExceptionIsThrown() {
        final MParticleOutgoingMessage validMessageWithURL = givenValidMessage("/messages/valid.json");

        when(mockResponse.isSuccessful()).thenReturn(false);
        when(mockResponse.code()).thenReturn(500);

        assertThrows(RetryLaterException.class, () -> testInstance.submit(validMessageWithURL));
    }

    @Test
    public void given400UnsuccessfulResponseThenRetryExceptionIsThrown() {
        final MParticleOutgoingMessage validMessageWithURL = givenValidMessage("/messages/valid.json");

        when(mockResponse.isSuccessful()).thenReturn(false);
        when(mockResponse.code()).thenReturn(400);

        assertThrows(RetryLaterException.class, () -> testInstance.submit(validMessageWithURL));
    }

    @Test
    void givenIOExceptionThenRetryLaterExceptionIsThrown() throws IOException {
        final MParticleOutgoingMessage validMessageWithURL = givenValidMessage("/messages/valid.json");

        when(mockCall.execute()).thenThrow(IOException.class);

        assertThrows(RetryLaterException.class, () -> testInstance.submit(validMessageWithURL));
    }

    @Test
    void givenRuntimeExceptionThenRetryLaterExceptionIsThrown() throws IOException {
        final MParticleOutgoingMessage validMessageWithURL = givenValidMessage("/messages/valid.json");

        when(mockCall.execute()).thenThrow(IOException.class);

        assertThrows(RetryLaterException.class, () -> testInstance.submit(validMessageWithURL));
    }

    @Test
    void givenValidMessageTheCorrectBatchIsSent() throws NoRetryException, RetryLaterException {
        final MParticleOutgoingMessage validMessageWithURL = givenValidMessage("/messages/valid.json");

        testInstance.submit(validMessageWithURL);

        verify(mockEventsApi).uploadEvents(batchCaptor.capture());

        final Batch result = batchCaptor.getValue();
        final UserIdentities userIdentities = result.getUserIdentities();
        assertThat(userIdentities.getEmail(), is(equalTo("REDACTED@gmail.com")));
        assertThat(result.getEvents().size(), is(2));

        final CustomEvent event1 = (CustomEvent) result.getEvents().get(0);
        assertThat(event1.getEventType(), is(CUSTOM_EVENT));

        final CustomEventData event1Data = event1.getData();
        assertThat(event1Data.getEventName(), is(equalTo(MParticleEventName.EMAIL_SUBSCRIBE.name())));
        assertThat(event1Data.getCustomEventType(), is(equalTo(CustomEventData.CustomEventType.OTHER)));

        final Map<String, String> customAttributes1 = event1Data.getCustomAttributes();
        assertThat(customAttributes1, hasEntry("client_id", "3386"));
        assertThat(customAttributes1, hasEntry("profile_id", "6634e1bd31a2a0e8af0b0dff"));
        assertThat(customAttributes1, hasEntry("list_id", "5609b2641aa312d6318b456b"));

        final CustomEvent event2 = (CustomEvent) result.getEvents().get(1);
        assertThat(event2.getEventType(), is(CUSTOM_EVENT));

        final CustomEventData event2Data = event2.getData();
        assertThat(event2Data.getEventName(), is(equalTo(MParticleEventName.EMAIL_UNSUBSCRIBE.name())));
        assertThat(event2Data.getCustomEventType(), is(equalTo(CustomEventData.CustomEventType.OTHER)));

        final Map<String, String> customAttributes2 = event2Data.getCustomAttributes();
        assertThat(customAttributes2, hasEntry("client_id", "3386"));
        assertThat(customAttributes2, hasEntry("profile_id", "7634e1bd31a2a0e8af0b0dfg"));
        assertThat(customAttributes2, hasEntry("list_id", "6609b2641aa312d6318b456d"));
    }

    @Test
    void givenApiURLIsProvidedInMessageThenItIsUsed() throws NoRetryException, RetryLaterException {
        final MParticleOutgoingMessage validMessageWithURL = givenValidMessage("/messages/valid.json");

        testInstance.submit(validMessageWithURL);

        verify(mockApiFactory).create("test_key", "test_secret", "https://test_url.com/");
    }

    @Test
    void givenApiURLIsNullInMessageThenDefaultIsUsed() throws NoRetryException, RetryLaterException {
        final MParticleOutgoingMessage validMessage = givenValidMessage("/messages/validWithoutURL.json");

        testInstance.submit(validMessage);

        verify(mockApiFactory).create("test_key", "test_secret", DEFAULT_BASE_URL);
    }

    @Test
    void givenApiURLIsMissingThenDefaultIsUsed() throws NoRetryException, RetryLaterException {
        final MParticleOutgoingMessage validMessage = givenValidMessage("/messages/validWithoutURL2.json");

        testInstance.submit(validMessage);

        verify(mockApiFactory).create("test_key", "test_secret", DEFAULT_BASE_URL);
    }

    @Test
    void givenApiURLWithoutTrailingSlashThenTrailingSlashIsAdded() throws NoRetryException, RetryLaterException {
        final MParticleOutgoingMessage validMessage = givenValidMessage("/messages/valid.json");
        final String httpbinUrl = "http://httpbin.org/status/200%3A99%2C429%3A1";
        validMessage.setApiURL(httpbinUrl);

        testInstance.submit(validMessage);

        verify(mockApiFactory).create("test_key", "test_secret", httpbinUrl + "/");
    }


    @Test
    void givenApiURLWithoutTrailingSlashContainingQueryThenTrailingSlashIsAdded()
        throws NoRetryException, RetryLaterException {
        final MParticleOutgoingMessage validMessage = givenValidMessage("/messages/valid.json");
        final String httpbinUrl = "http://httpbin.org/status/200%3A99%2C429%3A1";
        validMessage.setApiURL(httpbinUrl + "?test");

        testInstance.submit(validMessage);

        verify(mockApiFactory).create("test_key", "test_secret", httpbinUrl + "/?test");
    }

    private MParticleOutgoingMessage givenValidMessage(final String filePath) {
        lenient().when(mockResponse.isSuccessful()).thenReturn(true);

        try {
            final String json = loadResourceFileContent(filePath);
            return new ObjectMapper().readValue(json, MParticleOutgoingMessage.class);
        } catch (IOException | URISyntaxException | NullPointerException e) {
            fail("Unable to load file " + filePath + " from classpath");
            return null;
        }
    }

    private String loadResourceFileContent(final String path) throws IOException, URISyntaxException {
        return Files.readString(Paths.get(getClass().getResource(path).toURI()));
    }
}
