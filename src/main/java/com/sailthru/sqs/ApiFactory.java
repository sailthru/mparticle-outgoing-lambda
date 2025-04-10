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

public class ApiFactory {
    private static final String HTTP_POOL_KEEPALIVE_SECONDS = "HTTP_POOL_KEEPALIVE_SECONDS";
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiFactory.class);
    private static final OkHttpClient commonHttpClient = new OkHttpClient.Builder()
        .connectionPool(new ConnectionPool(5,
            safeParseInt(System.getenv(HTTP_POOL_KEEPALIVE_SECONDS), 300),
            TimeUnit.SECONDS))
        .build();
    private static final Map<ClientApiDetails, EventsApi> CACHE = new ConcurrentHashMap<>();

    public EventsApi of(final String apiKey, final String apiSecret, final String apiURL) {
        final ClientApiDetails apiDetails = new ClientApiDetails(apiKey, apiSecret, apiURL);
        return CACHE.computeIfAbsent(apiDetails, details -> {
            ApiClient client = new ApiClient(details.apiKey(), details.apiSecret());
            // use the same connection pool for everybody
            client.configureFromOkclient(commonHttpClient);
            client.getAdapterBuilder().baseUrl(details.apiURL());
            return client.createService(EventsApi.class);
        });
    }

    private record ClientApiDetails(String apiKey, String apiSecret, String apiURL) {
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
