package com.sailthru.sqs.metrics;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import software.amazon.cloudwatchlogs.emf.environment.LambdaEnvironment;
import software.amazon.cloudwatchlogs.emf.logger.MetricsLogger;
import software.amazon.cloudwatchlogs.emf.model.DimensionSet;
import software.amazon.cloudwatchlogs.emf.model.StorageResolution;
import software.amazon.cloudwatchlogs.emf.model.Unit;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class Metrics {
    private final Supplier<MetricsLogger> metricsFactory;

    public Metrics() {
        this(() -> new MetricsLogger(new LambdaEnvironment()));
    }

    public Metrics(Supplier<MetricsLogger> metricsFactory) {
        this.metricsFactory = metricsFactory;
    }

    public void mark(Context context, SQSEvent.SQSMessage sqsMessage, String name, long count) {
        withMetrics(metrics -> {
            if (sqsMessage != null) {
                metrics.putProperty("MessageId", sqsMessage.getMessageId());
            }
            if (context != null && context.getAwsRequestId() != null) {
                metrics.putProperty("function_request_id", context.getAwsRequestId());
            }
            metrics.putMetric("MessageTooLarge", 1, Unit.COUNT, StorageResolution.STANDARD);
        });
    }

    private void withMetrics(Consumer<MetricsLogger> consumer) {
        final MetricsLogger metrics = metricsFactory.get();
        metrics.putDimensions(DimensionSet.of("Service", "mparticle-outgoing-lambda"));
        try {
            consumer.accept(metrics);
        } finally {
            metrics.flush();
        }
    }
}
