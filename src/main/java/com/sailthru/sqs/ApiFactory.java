package com.sailthru.sqs;

import com.mparticle.ApiClient;
import com.mparticle.client.EventsApi;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.utils.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;

public class ApiFactory {
    private static final String HTTP_POOL_KEEPALIVE_SECONDS = "HTTP_POOL_KEEPALIVE_SECONDS";
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiFactory.class);
    private static final OkHttpClient commonHttpClient = new OkHttpClient.Builder()
        .connectionPool(new ConnectionPool(5,
            safeParseInt(System.getenv(HTTP_POOL_KEEPALIVE_SECONDS), 300),
            TimeUnit.SECONDS))
        .build();
    // VisibleForTesting
    static final Map<ClientApiDetails, EventsApi> CACHE = new ConcurrentHashMap<>();
    private static final ReentrantLock lock = new ReentrantLock();

    private BiFunction<String, String, ApiClient> apiClientFactory = ApiClient::new;

    public EventsApi of(final String apiKey, final String apiSecret, final String apiURL) {
        final ClientApiDetails apiDetails = new ClientApiDetails(apiKey, apiSecret, apiURL);
        return CACHE.computeIfAbsent(apiDetails, details -> {
            // we can only run one configuration segment at a time, globally, since ApiClient incorrectly
            // makes its OkHttpClient.Builder a static instance - so if you have multiple threads trying
            // to construct instances at the same time, you can run into trouble.
            // This makes the concurrent map slightly less performant, but only if we're hitting contention
            // due to multiple messages coming up at same time with different cache keys. In steady state
            // the lock will not be used.
            //
            // once the EventsApi instance is returned, we're OK because the OkHttpClient.Builder instance
            // was already called to obtain a new OkHttpClient instance, which is bound to the Retrofit
            // object.
            lock.lock();
            try {
                ApiClient client = apiClientFactory.apply(details.apiKey(), details.apiSecret());
                // use the same connection pool for everybody
                client.configureFromOkclient(commonHttpClient);
                client.getAdapterBuilder().baseUrl(details.apiURL());
                return client.createService(EventsApi.class);
            } finally {
                lock.unlock();
            }
        });
    }

    // VisibleForTesting
    void setApiClientFactory(BiFunction<String, String, ApiClient> apiClientFactory) {
        this.apiClientFactory = apiClientFactory;
    }

    // VisibleForTesting
    record ClientApiDetails(String apiKey, String apiSecret, String apiURL) {
    }

    private static int safeParseInt(String value, int defaultValue) {
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.warn("Unable to parse value '{}' as an integer, will use default {}", value, defaultValue);
            return defaultValue;
        }
    }
}
