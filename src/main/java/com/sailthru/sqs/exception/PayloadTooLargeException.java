package com.sailthru.sqs.exception;

import com.sailthru.sqs.message.MParticleOutgoingMessage;

public class PayloadTooLargeException extends RetryLaterException {
    private final long size;
    private final MParticleOutgoingMessage originalMessage;

    public PayloadTooLargeException(long size, MParticleOutgoingMessage originalMessage) {
        super("Payload is too large (" + size + " bytes)");
        this.size = size;
        this.originalMessage = originalMessage;
    }

    public long getSize() {
        return size;
    }

    public MParticleOutgoingMessage getOriginalMessage() {
        return originalMessage;
    }
}
