package com.sailthru.sqs.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MParticleOutgoingMessage {
    private String authenticationKey;
    private String authenticationSecret;
    private String profileEmail;
    private List<Event> events;
    private String timestamp;
    private String apiURL;

    public String getAuthenticationKey() {
        return authenticationKey;
    }

    public String getAuthenticationSecret() {
        return authenticationSecret;
    }

    public String getProfileEmail() {
        return profileEmail;
    }

    public void setAuthenticationKey(String authenticationKey) {
        this.authenticationKey = authenticationKey;
    }

    public void setAuthenticationSecret(String authenticationSecret) {
        this.authenticationSecret = authenticationSecret;
    }

    public void setProfileEmail(String profileEmail) {
        this.profileEmail = profileEmail;
    }

    public List<Event> getEvents() {
        return events;
    }

    public void setEvents(List<Event> events) {
        this.events = events;
    }

    public void setTimestamp(final String timestamp) {
        this.timestamp = timestamp;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getApiURL() {
        return apiURL;
    }

    public void setApiURL(String apiURL) {
        this.apiURL = apiURL;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Event {
        private MParticleEventName eventName;
        private MParticleEventType eventType;
        private Map<String, String> additionalData;

        public MParticleEventName getEventName() {
            return eventName;
        }

        public MParticleEventType getEventType() {
            return eventType;
        }

        public Map<String, String> getAdditionalData() {
            return additionalData;
        }

        public void setEventName(MParticleEventName eventName) {
            this.eventName = eventName;
        }

        public void setEventType(MParticleEventType eventType) {
            this.eventType = eventType;
        }

        public void setAdditionalData(Map<String, String> additionalData) {
            this.additionalData = Map.copyOf(additionalData);
        }
    }
}
