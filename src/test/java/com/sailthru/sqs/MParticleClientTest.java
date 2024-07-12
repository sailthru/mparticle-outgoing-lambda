package com.sailthru.sqs;

import com.mparticle.client.EventsApi;
import com.mparticle.model.Batch;
import com.sailthru.sqs.exception.NoRetryException;
import com.sailthru.sqs.exception.RetryLaterException;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Call;
import retrofit2.Response;
import java.io.IOException;
import java.util.Collections;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class MParticleClientTest {

    private MParticleClient mParticleClient;

    @Mock
    private EventsApi mockEventsApi;
    @Mock
    private Call<Void> mockCall;
    MParticleMessage message = generateMessage();
    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mParticleClient = spy(new MParticleClient());
        doReturn(mockEventsApi).when(mParticleClient).getEventsApi(anyString(), anyString());
    }

    @Test
    public void testSubmit_Successful() throws Exception {
        when(mockEventsApi.uploadEvents(any(Batch.class))).thenReturn(mockCall);
        Response<Void> mockResponse = Response.success(null);
        when(mockCall.execute()).thenReturn(mockResponse);

        assertDoesNotThrow(() -> mParticleClient.submit(message));
    }

    private MParticleMessage generateMessage() {
        MParticleMessage message = new MParticleMessage();
        message.setEventName("email_open");
        message.setEventType("OTHER");
        message.setAuthenticationKey("AuthKey123");
        message.setAuthenticationSecret("AuthSecret123");
        message.setProfileEmail("test@example.com");
        message.setAdditionalData(Collections.singletonMap("key", "value"));
        return message;
    }

    @Test
    public void testSubmit_RetryOn429WithRetryAfter() throws Exception {
        when(mockEventsApi.uploadEvents(any(Batch.class))).thenReturn(mockCall);
        Response<Void> mockResponse = Response.error(429, ResponseBody.create(MediaType.get("application/json"), ""));
        when(mockCall.execute()).thenReturn(mockResponse);

        assertThrows(RetryLaterException.class, () -> mParticleClient.submit(message));
    }

    @Test
    public void testSubmit_RetryOn500() throws Exception {
        when(mockEventsApi.uploadEvents(any(Batch.class))).thenReturn(mockCall);
        Response<Void> mockResponse = Response.error(500, ResponseBody.create(MediaType.get("application/json"), ""));
        when(mockCall.execute()).thenReturn(mockResponse);

        assertThrows(RetryLaterException.class, () -> mParticleClient.submit(message));
    }

    @Test
    public void testSubmit_NoRetryOn400() throws Exception {
        when(mockEventsApi.uploadEvents(any(Batch.class))).thenReturn(mockCall);
        Response<Void> mockResponse = Response.error(400, ResponseBody.create(MediaType.get("application/json"), ""));
        when(mockCall.execute()).thenReturn(mockResponse);

        assertThrows(NoRetryException.class, () -> mParticleClient.submit(message));
    }

    @Test
    public void testSubmit_RetryOnIOException() throws Exception {
        when(mockEventsApi.uploadEvents(any(Batch.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenThrow(new IOException());

        assertThrows(RetryLaterException.class, () -> mParticleClient.submit(message));
    }

    @Test
    public void testSubmit_RetryOnRuntimeException() throws Exception {
        when(mockEventsApi.uploadEvents(any(Batch.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenThrow(new RuntimeException());

        assertThrows(RetryLaterException.class, () -> mParticleClient.submit(message));
    }
}
