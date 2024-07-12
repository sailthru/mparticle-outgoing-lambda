package com.sailthru.sqs.exception;

public class RetryLaterException extends Exception {
    int responseCode;
    int retryAfter;

    public RetryLaterException(final Exception e) {
        super(e);
    }

    public RetryLaterException(int responseCode, int retryAfter) {
        super();
        this.responseCode = responseCode;
        this.retryAfter = retryAfter;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public int getRetryAfter() {
        return retryAfter;
    }
}
