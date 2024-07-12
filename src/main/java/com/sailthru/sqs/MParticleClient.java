package com.sailthru.sqs;

import com.mparticle.ApiClient;
import com.mparticle.client.EventsApi;
import com.mparticle.model.Batch;
import com.mparticle.model.CustomEvent;
import com.mparticle.model.CustomEventData;
import com.mparticle.model.UserIdentities;
import com.sailthru.sqs.exception.RetryLaterException;
import com.sailthru.sqs.message.MParticleOutgoingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;

public class MParticleClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageProcessor.class);
    private static final String BASE_URL = "https://inbound.mparticle.com/s2s/v2/";

    public void submit(final MParticleOutgoingMessage message) throws RetryLaterException {

        final Batch batch = prepareBatch(message);

        final EventsApi eventsApi = getEventsApi(message.getAuthenticationKey(), message.getAuthenticationSecret());

        LOGGER.debug("Attempting to send batch: {} for message: {}", batch, message);

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

    private Batch prepareBatch(final MParticleOutgoingMessage message) {
        final Batch batch = new Batch();
        batch.environment(Batch.Environment.DEVELOPMENT);
        batch.userIdentities(new UserIdentities()
                .email(message.getProfileEmail())
        );

        message.getEvents().forEach(event -> {
            final CustomEvent customEvent = new CustomEvent().data(
                    new CustomEventData()
                            .eventName(event.getEventName().name())
                            .customEventType(CustomEventData.CustomEventType.valueOf(event.getEventType().name()))
            );

            customEvent.getData().customAttributes(event.getAdditionalData());

            batch.addEventsItem(event);
        });

        return batch;
    }
}
