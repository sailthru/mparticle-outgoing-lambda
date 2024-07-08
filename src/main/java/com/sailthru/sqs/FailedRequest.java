package com.sailthru.sqs;

public class FailedRequest {

    int statusCode;
    int retryAfter;
    String receiptHandle;

    public FailedRequest(int statusCode, int retryAfter) {
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
    }
    public FailedRequest(String receiptHandle) {
        this.receiptHandle = receiptHandle;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public int getRetryAfter() {
        return retryAfter;
    }

    public String getReceiptHandle() {
        return receiptHandle;
    }

    public FailedRequest setReceiptHandle(String receiptHandle) {
        this.receiptHandle = receiptHandle;
        return this;
    }
}
