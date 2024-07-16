package com.sailthru.sqs.exception;

public class NoRetryException extends Exception {
    int statusCode;

    public NoRetryException(final String message) {
        super(message);
    }

    public NoRetryException(final int statusCode, final String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public NoRetryException(final String message, final Exception e) {
        super(message, e);
    }
}
