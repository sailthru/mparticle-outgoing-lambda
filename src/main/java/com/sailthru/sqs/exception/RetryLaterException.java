package com.sailthru.sqs.exception;

public class RetryLaterException extends Exception {
    private final int statusCode;
    private final long retryAfter;

    // used for subclasses
    protected RetryLaterException(String message) {
        super(message);
        this.statusCode = 0;
        this.retryAfter = 1;
    }

    public RetryLaterException(final Exception e) {
        super(e);
        this.statusCode = 0;
        this.retryAfter = 0;
    }

    public RetryLaterException(int statusCode, String message, long retryAfter) {
        super(message);
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public long getRetryAfter() {
        return retryAfter;
    }
}
