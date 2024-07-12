package com.sailthru.sqs;

public class FailedRequest {

    int statusCode;
    int retryAfter;
    String receiptHandle;
    int receiveCount;

    public FailedRequest(int statusCode, int retryAfter, String receiptHandle, int approximateReceiveCount) {
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
        this.receiptHandle = receiptHandle;
        this.receiveCount = approximateReceiveCount;
    }
    public FailedRequest(String receiptHandle, int approximateReceiveCount) {
        this.receiptHandle = receiptHandle;
        this.receiveCount = approximateReceiveCount;
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

    public int getReceiveCount() {
        return receiveCount;
    }

    public FailedRequest setReceiptHandle(String receiptHandle) {
        this.receiptHandle = receiptHandle;
        return this;
    }
}
