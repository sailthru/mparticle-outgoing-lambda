package com.sailthru.sqs.exception;

public class NoRetryException extends Exception {
    public NoRetryException(final String message) {
        super(message);
    }

    public NoRetryException(final String message, final Exception e) {
        super(message, e);
    }
}
