package com.sailthru.sqs.metrics;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.hamcrest.CustomTypeSafeMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.cloudwatchlogs.emf.logger.MetricsLogger;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MetricsTest {
    @Mock
    private Context context;
    private Metrics metrics;
    private DummyEnvironment dummyEnvironment;

    @BeforeEach
    void beforeEach() {
        this.dummyEnvironment = new DummyEnvironment();
        this.metrics = new Metrics(() -> new MetricsLogger(dummyEnvironment));
    }

    @Test
    void givenExpectedMetricNameThenMetricSentOut() {
        final String requestId = UUID.randomUUID().toString();
        final String messageId = UUID.randomUUID().toString();
        final var message = givenStanderdMessage(requestId, messageId);

        metrics.mark(context, message, Metrics.MESSAGE_TOO_LARGE, 1);
        final var events = dummyEnvironment.getEvents();

        assertThat(events,
            contains(
                allOf(
                    standardProperties(),
                    matchesRequestId(requestId),
                    matchesMessageId(messageId),
                    matchesMetric(Metrics.MESSAGE_TOO_LARGE, "Count", 1)
                )
            )
        );
    }

    @Test
    void givenOtherMetricNameThenMetricSentOut() {
        final String requestId = UUID.randomUUID().toString();
        final String messageId = UUID.randomUUID().toString();
        final var message = givenStanderdMessage(requestId, messageId);

        metrics.mark(context, message, "TestMetric", 1);
        final var events = dummyEnvironment.getEvents();

        assertThat(events,
            contains(
                allOf(
                    standardProperties(),
                    matchesRequestId(requestId),
                    matchesMessageId(messageId),
                    matchesMetric("TestMetric", "Count", 1)
                )
            )
        );
    }

    private SQSEvent.SQSMessage givenStanderdMessage(String requestId, String messageId) {
        when(context.getAwsRequestId()).thenReturn(requestId);
        final SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setMessageId(messageId);
        return message;
    }

    private Matcher<DummyEnvironment.LogEvents> matchesMetric(String metricName, String unit, double value) {
        return new CustomTypeSafeMatcher<>("contains metric " + metricName + " with value " + value) {
            @Override
            protected boolean matchesSafely(DummyEnvironment.LogEvents logEvents) {
                return logEvents.metadata().metricsDefinitions().size() == 1 &&
                    logEvents.metadata().metricsDefinitions().stream()
                        .findFirst()
                        .get()
                        .metrics().size() == 1 &&
                    logEvents.metadata().metricsDefinitions().stream()
                        .findFirst()
                        .get()
                        .metrics().stream()
                        .findFirst()
                        .get()
                        .name().equals(metricName) &&
                    logEvents.metadata().metricsDefinitions().stream()
                        .findFirst()
                        .get()
                        .metrics().stream()
                        .findFirst()
                        .get()
                        .unit().equals(unit) &&
                    logEvents.props().size() == 1 &&
                    logEvents.props().get(metricName).equals(value);
            }
        };
    }

    private Matcher<DummyEnvironment.LogEvents> matchesMessageId(String messageId) {
        return new CustomTypeSafeMatcher<>("matches message Id " + messageId) {
            @Override
            protected boolean matchesSafely(DummyEnvironment.LogEvents logEvents) {
                return logEvents.messageId().equals(messageId);
            }
        };
    }

    private Matcher<DummyEnvironment.LogEvents> matchesRequestId(String requestId) {
        return new CustomTypeSafeMatcher<>("matches request Id " + requestId) {
            @Override
            protected boolean matchesSafely(DummyEnvironment.LogEvents logEvents) {
                return logEvents.requestId().equals(requestId);
            }
        };
    }

    private Matcher<DummyEnvironment.LogEvents> standardProperties() {
        return new CustomTypeSafeMatcher<>("has standard logging properties") {
            @Override
            public boolean matchesSafely(DummyEnvironment.LogEvents logEvents) {
                return logEvents.metadata().timestamp() != 0
                    && logEvents.metadata().metricsDefinitions().size() == 1
                    && logEvents.metadata().metricsDefinitions().stream()
                        .findFirst()
                        .get()
                        .dimensions().size() == 1
                    && logEvents.metadata().metricsDefinitions().stream()
                        .findFirst()
                        .get()
                        .dimensions().stream()
                        .findFirst()
                        .get()
                        .containsAll(Set.of("LogGroup", "ServiceName", "ServiceType", "Service"))
                    && !logEvents.metadata().metricsDefinitions().stream()
                        .findFirst()
                        .get()
                        .dimensions().contains("function_request_id")
                    && !logEvents.metadata().metricsDefinitions().stream()
                        .findFirst()
                        .get()
                        .dimensions().contains("MessageId")
                    && logEvents.logGroup().equals("test-metrics")
                    && logEvents.serviceName().equals("test-name")
                    && logEvents.serviceType().equals("test-type")
                    && logEvents.service().equals("mparticle-outgoing-lambda")
                    && logEvents.metadata().metricsDefinitions().stream()
                        .findFirst()
                        .get()
                        .namespace().equals("aws-embedded-metrics")
                    && logEvents.metadata().metricsDefinitions().stream()
                        .findFirst()
                        .get()
                        .metrics().size() == 1;
            }
        };
    }
}
