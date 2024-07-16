package com.sailthru.sqs;

public class FailedRequest {

    private final String id;
    private final int statusCode;
    private final int retryAfter;
    private final String receiptHandle;
    private final int receiveCount;

    public FailedRequest(String id, int statusCode, int retryAfter, String receiptHandle, int approximateReceiveCount) {
        this.id = id;
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
        this.receiptHandle = receiptHandle;
        this.receiveCount = approximateReceiveCount;
    }

    public String getId() {
        return id;
    }

    public int getRetryAfter() {
        return retryAfter;
    }

    public String getReceiptHandle() {
        return receiptHandle;
    }

    public int getReceiveCount() {
        return receiveCount;
    }
}
