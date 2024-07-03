package com.sailthru.sqs;

import com.mparticle.ApiClient;
import com.mparticle.client.EventsApi;
import com.mparticle.model.Batch;
import com.mparticle.model.CustomEvent;
import com.mparticle.model.CustomEventData;
import com.mparticle.model.UserIdentities;
import com.sailthru.sqs.exception.RetryLaterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;

public class MParticleClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageProcessor.class);
    private static final String BASE_URL = "https://inbound.mparticle.com/s2s/v2/";

    public void submit(final MParticleMessage message) throws RetryLaterException {

        final Batch batch = prepareBatch(message);

        final EventsApi eventsApi = getEventsApi(message.getAuthenticationKey(), message.getAuthenticationSecret());

        LOGGER.info("Attempting to send batch: {} for message: {}", batch, message);

        final Call<Void> singleResult = eventsApi.uploadEvents(batch);

        try {
            final Response<Void> response = singleResult.execute();
            LOGGER.info("Received response code: {}", response.code());

            if (!response.isSuccessful()) {
                throw new RetryLaterException();
            }

            LOGGER.info("Successfully sent message: {}", message);
        } catch (IOException e) {
            //Retry for all IOExceptions
            throw new RetryLaterException(e);
        }
    }

    private EventsApi getEventsApi(final String apiKey, final String apiSecret) {
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
