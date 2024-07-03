package com.sailthru.sqs.exception;

public class AuthenticationKeyNotProvidedException extends NoRetryException {
    public AuthenticationKeyNotProvidedException(final String message) {
        super(message);
    }

    public AuthenticationKeyNotProvidedException(final String message, final Exception e) {
        super(message, e);
    }
}
