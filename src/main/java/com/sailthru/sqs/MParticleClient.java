package com.sailthru.sqs;

import com.mparticle.ApiClient;
import com.mparticle.client.EventsApi;
import com.mparticle.model.Batch;
import com.mparticle.model.CustomEvent;
import com.mparticle.model.CustomEventData;
import com.mparticle.model.UserIdentities;
import com.sailthru.sqs.exception.NoRetryException;
import com.sailthru.sqs.exception.RetryLaterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.Objects;

public class MParticleClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageProcessor.class);
    private static final String BASE_URL = "https://inbound.mparticle.com/s2s/v2/";
    private static final String retryAfterHeaderKey = "Retry-After";

    public void submit(final MParticleMessage message) throws RetryLaterException, NoRetryException {

        final Batch batch = prepareBatch(message);

        final EventsApi eventsApi = getEventsApi(message.getAuthenticationKey(), message.getAuthenticationSecret());

        LOGGER.debug("Attempting to send batch: {} for message: {}", batch, message);

        final Call<Void> singleResult = eventsApi.uploadEvents(batch);

        try {
            final Response<Void> response = singleResult.execute();
            LOGGER.info("Received response code: {}", response.code());

            if (!response.isSuccessful()) {
                int statusCode = response.code();
                int retryAfter = 0;

                if (statusCode == 429) {
                    String retryAfterHeader = response.headers().get(retryAfterHeaderKey);
                    if (retryAfterHeader != null) {
                        try {
                            retryAfter = Integer.parseInt(retryAfterHeader);
                        } catch (NumberFormatException e) {
                            LOGGER.warn("Invalid Retry-After header value: {}. Defaulting to 0.", retryAfterHeader);
                        }
                    } else {
                        LOGGER.warn("Missing Retry-After header for status code 429. Defaulting to 0.");
                    }
                    throw new RetryLaterException(statusCode, retryAfter);
                } else if (statusCode >= 500 && statusCode < 600) {
                    throw new RetryLaterException(statusCode, retryAfter);
                }
                //Do not retry for all status code except 429 and status code between 500 and 600
                throw new NoRetryException(response.message());
            }

            LOGGER.info("Successfully sent message: {}", message);
        } catch (IOException e) {
            //Retry for all IOExceptions
            throw new RetryLaterException(e);
        }
    }

    protected EventsApi getEventsApi(final String apiKey, final String apiSecret) {
        final ApiClient apiClient = new ApiClient(apiKey, apiSecret);

        apiClient.getAdapterBuilder().baseUrl(BASE_URL);

        return apiClient.createService(EventsApi.class);
    }

    private Batch prepareBatch(final MParticleMessage message) {
        final Batch batch = new Batch();
        batch.environment(Batch.Environment.DEVELOPMENT);
        batch.userIdentities(new UserIdentities()
                .email(message.getProfileEmail())
        );

        final CustomEvent event = new CustomEvent().data(
                new CustomEventData()
                        .eventName(message.getEventName())
                        .customEventType(CustomEventData.CustomEventType.valueOf(message.getEventType()))
        );

        event.getData().customAttributes(message.getAdditionalData());

        batch.addEventsItem(event);

        return batch;
    }
}
