package com.sailthru.sqs.exception;

import com.sailthru.sqs.FailedRequest;

public class RetryLaterException extends Exception {
    public RetryLaterException(final Exception e) {
        super(e);
    }

    FailedRequest failedRequest;

    public RetryLaterException(int responseCode, int retryAfter) {
        super();
        failedRequest = new FailedRequest(responseCode, retryAfter);
    }

    public FailedRequest getFailedRequest() {
        return failedRequest;
    }
}
