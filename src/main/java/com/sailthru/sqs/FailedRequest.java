package com.sailthru.sqs;

import java.time.Duration;

public class FailedRequest {

    private final String id;
    private final int statusCode;
    private final long retryAfter;
    private final String receiptHandle;
    private final int receiveCount;

    public FailedRequest(
        String id,
        int statusCode,
        long retryAfter,
        String receiptHandle,
        int approximateReceiveCount
    ) {
        this.id = id;
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
        this.receiptHandle = receiptHandle;
        this.receiveCount = approximateReceiveCount;
    }

    public String getId() {
        return id;
    }

    public long getRetryAfter() {
        return retryAfter;
    }

    public String getReceiptHandle() {
        return receiptHandle;
    }

    public int getReceiveCount() {
        return receiveCount;
    }
}
