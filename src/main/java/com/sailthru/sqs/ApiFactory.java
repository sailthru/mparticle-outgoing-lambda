package com.sailthru.sqs;

import com.mparticle.ApiClient;
import com.mparticle.client.EventsApi;

public class ApiFactory {
    public EventsApi create(final String apiKey, final String apiSecret, final String apiURL) {
        final ApiClient apiClient = new ApiClient(apiKey, apiSecret);

        apiClient.getAdapterBuilder().baseUrl(apiURL);

        return apiClient.createService(EventsApi.class);
    }
}
