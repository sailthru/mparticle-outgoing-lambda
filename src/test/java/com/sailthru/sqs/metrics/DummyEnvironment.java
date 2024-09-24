package com.sailthru.sqs.metrics;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.cloudwatchlogs.emf.environment.Environment;
import software.amazon.cloudwatchlogs.emf.model.MetricsContext;
import software.amazon.cloudwatchlogs.emf.sinks.ISink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.fail;

public class DummyEnvironment implements Environment, ISink {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final List<String> events = new ArrayList<>();

    public void reset() {
        events.clear();
    }

    public List<LogEvents> getEvents() {
        return events.stream()
            .map(s -> {
                try {
                    return MAPPER.readValue(s, LogEvents.class);
                } catch (JsonProcessingException e) {
                    fail(e.getMessage());
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public boolean probe() {
        return false;
    }

    @Override
    public String getName() {
        return "test-name";
    }

    @Override
    public String getType() {
        return "test-type";
    }

    @Override
    public String getLogGroupName() {
        return "test-metrics";
    }

    @Override
    public void configureContext(MetricsContext context) {
        // noop
    }

    @Override
    public ISink getSink() {
        return this;
    }

    @Override
    public void accept(MetricsContext context) {
        try {
            for (var evt: context.serialize()) {
                events.add(evt);
            }
        } catch (JsonProcessingException e) {
            fail(e.getMessage());
        }
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LogMetricDefinition(
        @JsonProperty("Name") String name,
        @JsonProperty("Unit") String unit
    ) { }

    record LogMetricsDefinition(
        @JsonProperty("Namespace") String namespace,
        @JsonProperty("Dimensions") List<Set<String>> dimensions,
        @JsonProperty("Metrics") List<LogMetricDefinition> metrics
    ) { }

    record LogMetadata(
        @JsonProperty("Timestamp") long timestamp,
        @JsonProperty("CloudWatchMetrics") List<LogMetricsDefinition> metricsDefinitions
    ) { }

    record LogEvents(
        LogMetadata metadata,
        @JsonProperty("function_request_id") String requestId,
        @JsonProperty("LogGroup") String logGroup,
        @JsonProperty("ServiceName") String serviceName,
        @JsonProperty("ServiceType") String serviceType,
        @JsonProperty("Service") String service,
        @JsonProperty("MessageId") String messageId,
        Map<String, Object> props
    ) {
        @JsonCreator
        LogEvents(
            @JsonProperty("_aws") LogMetadata metadata,
            @JsonProperty("function_request_id") String requestId,
            @JsonProperty("LogGroup") String logGroup,
            @JsonProperty("ServiceName") String serviceName,
            @JsonProperty("ServiceType") String serviceType,
            @JsonProperty("Service") String service,
            @JsonProperty("MessageId") String messageId
        ) {
            this(metadata, requestId, logGroup, serviceName, serviceType, service, messageId, new HashMap<>());
        }

        @JsonAnyGetter
        public Map<String, Object> props() {
            return Map.copyOf(props);
        }

        @JsonAnySetter
        public void setProps(String name, Object value) {
            props.put(name, value);
        }
    }
}
