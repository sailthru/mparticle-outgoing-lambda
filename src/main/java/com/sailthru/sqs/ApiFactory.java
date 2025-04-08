package com.sailthru.sqs;

import com.mparticle.ApiClient;
import com.mparticle.client.EventsApi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ApiFactory {

    private static final Map<ClientApiDetails, EventsApi> CACHE = new ConcurrentHashMap<>();

    public EventsApi of(final String apiKey, final String apiSecret, final String apiURL) {
        final ClientApiDetails apiDetails = new ClientApiDetails(apiKey, apiSecret, apiURL);
        return CACHE.computeIfAbsent(apiDetails, details -> {
            ApiClient client = new ApiClient(details.apiKey(), details.apiSecret());
            client.getAdapterBuilder().baseUrl(details.apiURL());
            return client.createService(EventsApi.class);
        });
    }

    private record ClientApiDetails(String apiKey, String apiSecret, String apiURL) {
    }

}
