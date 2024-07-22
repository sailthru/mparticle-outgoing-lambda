package com.sailthru.sqs;

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
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import static java.lang.String.format;
import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;

public class MParticleClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageProcessor.class);
    static final String DEFAULT_BASE_URL = "https://inbound.mparticle.com/s2s/v2/";

    private ApiFactory apiFactory = new ApiFactory();

    public void submit(final MParticleOutgoingMessage message) throws RetryLaterException {

        final Batch batch = prepareBatch(message);

        final EventsApi eventsApi = getEventsApi(message);

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

    private EventsApi getEventsApi(final MParticleOutgoingMessage message) {
        final String apiKey = message.getAuthenticationKey();
        final String apiSecret = message.getAuthenticationSecret();
        final String apiURL = Optional.ofNullable(message.getApiURL())
                .filter(not(String::isEmpty))
                .orElse(DEFAULT_BASE_URL);

        return apiFactory.create(apiKey, apiSecret, apiURL);
    }

    private Batch prepareBatch(final MParticleOutgoingMessage message) {
        final Batch batch = new Batch();
        batch.environment(Batch.Environment.DEVELOPMENT);
        batch.userIdentities(new UserIdentities()
                .email(message.getProfileEmail())
        );
        batch.timestampUnixtimeMs(parseTimestamp(message.getTimestamp()));

        message.getEvents().forEach(event -> {
            final CustomEvent customEvent = new CustomEvent().data(
                    new CustomEventData()
                            .eventName(event.getEventName().name())
                            .customEventType(CustomEventData.CustomEventType.valueOf(event.getEventType().name()))
            );

            customEvent.getData().customAttributes(event.getAdditionalData());

            batch.addEventsItem(customEvent);
        });

        return batch;
    }

    private Long parseTimestamp(String timestamp) {
        try {
            if (!ofNullable(timestamp).orElse("").isEmpty()) {
                final ZonedDateTime dt = ZonedDateTime.parse(timestamp, ISO_DATE_TIME);
                return dt.toInstant().toEpochMilli();
            }
        } catch (DateTimeParseException e) {
            LOGGER.warn(format("Failed to parse timestamp: %s", timestamp));
        }
        return null;
    }

    public void setApiFactory(final ApiFactory apiFactory) {
        this.apiFactory = apiFactory;
    }
}
