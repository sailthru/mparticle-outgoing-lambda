package com.sailthru.sqs.exception;

public class RetryLaterException extends Exception {
    public RetryLaterException(final Exception e) {
        super(e);
    }

    public RetryLaterException() {
        super();
    }
}
