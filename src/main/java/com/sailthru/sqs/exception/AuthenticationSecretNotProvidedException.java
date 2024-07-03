package com.sailthru.sqs.exception;

public class AuthenticationSecretNotProvidedException extends NoRetryException {
    public AuthenticationSecretNotProvidedException(final String message) {
        super(message);
    }

    public AuthenticationSecretNotProvidedException(final String message, final Exception e) {
        super(message, e);
    }
}
