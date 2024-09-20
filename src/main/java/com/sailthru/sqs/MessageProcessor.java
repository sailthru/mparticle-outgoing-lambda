package com.sailthru.sqs;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.sailthru.sqs.exception.AuthenticationKeyNotProvidedException;
import com.sailthru.sqs.exception.AuthenticationSecretNotProvidedException;
import com.sailthru.sqs.exception.NoRetryException;
import com.sailthru.sqs.exception.PayloadTooLargeException;
import com.sailthru.sqs.exception.RetryLaterException;
import com.sailthru.sqs.exception.UnparseablePayloadException;
import com.sailthru.sqs.message.MParticleOutgoingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.utils.StringUtils;

import java.io.IOException;

public class MessageProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageProcessor.class);
    private static final int MAX_MPARTICLE_MESSAGE_LENGTH = 256 * 1000; // 256 kb - rounded down in case they messed up

    private MessageSerializer messageSerializer;
    private boolean mparticleDisabled;
    private ApiFactory apiFactory;
    private MParticleClient mParticleClient;

    public MessageProcessor(boolean mparticleDisabled) {
        messageSerializer = new MessageSerializer();
        apiFactory = new ApiFactory();
        mParticleClient = new MParticleClient(apiFactory);
        this.mparticleDisabled = mparticleDisabled;
    }

    public void process(final SQSEvent.SQSMessage sqsMessage) throws RetryLaterException, NoRetryException {
        final String rawMessage = sqsMessage.getBody();
        LOGGER.debug("Received message: {}", rawMessage);

        final MParticleOutgoingMessage message = parseAndValidateMessage(rawMessage);

        if (!mparticleDisabled) {
            getMParticleClient().submit(message);
        }
    }

    private MParticleOutgoingMessage parseAndValidateMessage(final String rawMessage) throws NoRetryException, PayloadTooLargeException {
        try {
            final MParticleOutgoingMessage message = getSerializer().deserialize(rawMessage,
                    MParticleOutgoingMessage.class);

            if (StringUtils.isEmpty(message.getAuthenticationKey())) {
                throw new AuthenticationKeyNotProvidedException("Authentication key not provided.");
            }

            if (StringUtils.isEmpty(message.getAuthenticationSecret())) {
                throw new AuthenticationSecretNotProvidedException("Authentication secret not provided.");
            }

            // check the size of the message
            int outgoingMessageLength = messageSerializer.serialize(message.toBatch()).length;
            if (outgoingMessageLength > MAX_MPARTICLE_MESSAGE_LENGTH) {
                throw new PayloadTooLargeException(outgoingMessageLength);
            }

            return message;
        } catch (IOException e) {
            throw new UnparseablePayloadException(String.format("Could not deserialize message: %s", rawMessage), e);
        }
    }

    private MParticleClient getMParticleClient() {
        return mParticleClient;
    }

    private MessageSerializer getSerializer() {
        return messageSerializer;
    }

    // @VisibleForTesting
    public void setMParticleClient(final MParticleClient mParticleClient) {
        this.mParticleClient = mParticleClient;
    }
}
