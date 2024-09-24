package com.sailthru.sqs.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mparticle.model.Batch;
import com.mparticle.model.CustomEvent;
import com.mparticle.model.CustomEventData;
import com.mparticle.model.UserIdentities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static java.util.Optional.ofNullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MParticleOutgoingMessage {
    private String authenticationKey;
    private String authenticationSecret;
    private long clientId;
    private String profileEmail;
    private String profileMpId;
    private List<Event> events = List.of();
    private String timestamp;
    private String apiURL;

    @JsonIgnore
    private Batch batch;
    private static final Logger LOGGER = LoggerFactory.getLogger(MParticleOutgoingMessage.class);

    public String getAuthenticationKey() {
        return authenticationKey;
    }

    public String getAuthenticationSecret() {
        return authenticationSecret;
    }

    public String getProfileEmail() {
        return profileEmail;
    }

    public String getProfileMpId() {
        return profileMpId;
    }

    public long getClientId() {
        return clientId;
    }

    public void setAuthenticationKey(String authenticationKey) {
        batch = null;
        this.authenticationKey = authenticationKey;
    }

    public void setAuthenticationSecret(String authenticationSecret) {
        batch = null;
        this.authenticationSecret = authenticationSecret;
    }

    public void setProfileEmail(String profileEmail) {
        this.profileEmail = profileEmail;
    }

    public void setProfileMpId(String profileMpId) {
        batch = null;
        this.profileMpId = profileMpId;
    }

    public void setClientId(long clientId) {
        this.clientId = clientId;
    }

    public List<Event> getEvents() {
        return List.copyOf(events);
    }

    public void setEvents(List<Event> events) {
        batch = null;
        this.events = events;
    }

    public void setTimestamp(final String timestamp) {
        batch = null;
        this.timestamp = timestamp;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getApiURL() {
        return apiURL;
    }

    public void setApiURL(String apiURL) {
        batch = null;
        this.apiURL = apiURL;
    }

    @JsonIgnore
    public Batch toBatch() {
        if (batch == null) {
            batch = new Batch();
            batch.environment(Batch.Environment.DEVELOPMENT);
            batch.userIdentities(new UserIdentities()
                .email(getProfileEmail())
            );
            if (getProfileMpId() != null) {
                batch.mpid(Long.parseUnsignedLong(getProfileMpId(), 16));
            }
            batch.timestampUnixtimeMs(parseTimestamp(getTimestamp()));
            batch.setEvents(getEvents().stream()
                .map(Event::toBatchEvent)
                .toList());
        }

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
            return Map.copyOf(additionalData);
        }

        @JsonIgnore
        public Object toBatchEvent() {
            return new CustomEvent().data(
                (CustomEventData) new CustomEventData()
                    .eventName(getEventName().name())
                    .customEventType(CustomEventData.CustomEventType.valueOf(getEventType().name()))
                    .customAttributes(getAdditionalData())
            );
        }
    }
}
