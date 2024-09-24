package com.sailthru.sqs;

import com.mparticle.client.EventsApi;
import com.mparticle.model.Batch;
import com.sailthru.sqs.exception.NoRetryException;
import com.sailthru.sqs.exception.RetryLaterException;
import com.sailthru.sqs.message.MParticleOutgoingMessage;
import okhttp3.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.function.Predicate.not;

public class MParticleClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageProcessor.class);
    private static final String RETRY_AFTER_HEADER = "Retry-After";
    // @VisibleForTesting
    static final int TOO_MANY_REQUESTS = 429;
    // @VisibleForTesting
    static final String DEFAULT_BASE_URL = "https://inbound.mparticle.com/s2s/v2/";

    private final ApiFactory apiFactory;

    public MParticleClient(ApiFactory apiFactory) {
        this.apiFactory = apiFactory;
    }

    public void submit(final MParticleOutgoingMessage message) throws RetryLaterException, NoRetryException {
        final Instant now = Instant.now();

        final Batch batch = message.toBatch();

        final EventsApi eventsApi = getEventsApi(message);

        LOGGER.debug("Attempting to send batch: {} for message: {}", batch, message);

        final Call<Void> singleResult = eventsApi.uploadEvents(batch);

        try {
            final Response<Void> response = singleResult.execute();
            LOGGER.info("Received response code: {}", response.code());

            if (!response.isSuccessful()) {
                int statusCode = response.code();

                if (statusCode == TOO_MANY_REQUESTS) {
                    throw new RetryLaterException(statusCode, response.message(),
                        parseRetryAfter(response.headers(), now));
                } else if (isRetryLaterStatusCode(statusCode)) {
                    throw new RetryLaterException(statusCode, response.message(), 0);
                }
                //Do not retry for all status code except 429 and status code between 400 and 600 (excl)
                throw new NoRetryException(statusCode, response.message());
            }

            LOGGER.debug("Successfully sent message: {}", message);
        } catch (IOException | RuntimeException e) {
            throw new RetryLaterException(e);
        }
    }

    private long parseRetryAfter(Headers headers, Instant now) {
        final String retryAfterHeader = headers.get(RETRY_AFTER_HEADER);
        if (retryAfterHeader != null) {
            // try to parse as an int
            try {
                return Long.parseLong(retryAfterHeader);
            } catch (NumberFormatException e) {
                // try as a date - use the okhttp stuff to do it for us
                final Instant instant = headers.getInstant(RETRY_AFTER_HEADER);
                if (instant != null) {
                    if (now.isBefore(instant)) {
                        return Duration.between(now, instant).toSeconds();
                    } else {
                        // minimum
                        return 1L;
                    }
                }
            }
            LOGGER.warn("Unable to parse retry after header: {}, will use default", retryAfterHeader);
        } else {
            LOGGER.info("Retry-after header missing from response. Will use default.");
        }
        return 0L;
    }

    private boolean isRetryLaterStatusCode(int statusCode) {
        return statusCode >= 400 && statusCode < 600;
    }

    private EventsApi getEventsApi(final MParticleOutgoingMessage message) {
        final String apiKey = message.getAuthenticationKey();
        final String apiSecret = message.getAuthenticationSecret();
        final String apiURL = Optional.ofNullable(message.getApiURL())
                .filter(not(String::isEmpty))
                .orElse(DEFAULT_BASE_URL);

        return apiFactory.create(apiKey, apiSecret, apiURL);
    }
}
