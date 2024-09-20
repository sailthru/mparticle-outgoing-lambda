package com.sailthru.sqs.exception;

public class PayloadTooLargeException extends RetryLaterException {
    private final long size;

    public PayloadTooLargeException(long size) {
        super("Payload is too large (" + size + " bytes)");
        this.size = size;
    }

    public long getSize() {
        return size;
    }
}
