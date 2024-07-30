package com.sailthru.sqs.exception;

public class NoRetryException extends Exception {
    final int statusCode;

    public NoRetryException(final String message) {
        super(message);
        this.statusCode = 0;
    }

    public NoRetryException(final int statusCode, final String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public NoRetryException(final String message, final Exception e) {
        super(message, e);
        this.statusCode = 0;
    }
}
