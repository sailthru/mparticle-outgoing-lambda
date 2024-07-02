package com.sailthru.sqs;

import java.util.Map;

public class MParticleMessage {
    private String authenticationKey;
    private String authenticationSecret;
    private String eventName;
    private String eventType;
    private Map<String, String> additionalData;
    private String profileEmail;

    public String getAuthenticationKey() {
        return authenticationKey;
    }

    public String getAuthenticationSecret() {
        return authenticationSecret;
    }

    public String getEventName() {
        return eventName;
    }

    public String getEventType() {
        return eventType;
    }

    public Map<String, String> getAdditionalData() {
        return additionalData;
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

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public void setAdditionalData(Map<String, String> additionalData) {
        this.additionalData = additionalData;
    }

    public void setProfileEmail(String profileEmail) {
        this.profileEmail = profileEmail;
    }
}
