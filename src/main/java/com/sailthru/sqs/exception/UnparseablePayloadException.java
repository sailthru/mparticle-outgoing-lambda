package com.sailthru.sqs.exception;

public class UnparseablePayloadException extends NoRetryException {
    public UnparseablePayloadException(final String message) {
        super(message);
    }

    public UnparseablePayloadException(final String message, final Exception e) {
        super(message, e);
    }
}
