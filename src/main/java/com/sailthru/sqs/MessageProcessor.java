package com.sailthru.sqs;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.sailthru.sqs.exception.AuthenticationKeyNotProvidedException;
import com.sailthru.sqs.exception.AuthenticationSecretNotProvidedException;
import com.sailthru.sqs.exception.NoRetryException;
import com.sailthru.sqs.exception.RetryLaterException;
import com.sailthru.sqs.exception.UnparseablePayloadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.utils.StringUtils;

import java.io.IOException;

public class MessageProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageProcessor.class);

    private MessageSerializer messageSerializer;
    private MParticleClient mParticleClient;

    public MessageProcessor() {
        messageSerializer = new MessageSerializer();
        mParticleClient = new MParticleClient();
    }

    public void process(final SQSEvent.SQSMessage sqsMessage) throws RetryLaterException, NoRetryException {
        final String rawMessage = sqsMessage.getBody();
        LOGGER.debug("Received message: {}", rawMessage);

        final MParticleMessage message = parseAndValidateMessage(rawMessage);

        getMParticleClient().submit(message);
    }

    private MParticleMessage parseAndValidateMessage(final String rawMessage) throws NoRetryException {
        try {
            final MParticleMessage message = getSerializer().deserialize(rawMessage, MParticleMessage.class);

            if (StringUtils.isEmpty(message.getAuthenticationKey())) {
                throw new AuthenticationKeyNotProvidedException("Authentication key not provided.");
            }

            if (StringUtils.isEmpty(message.getAuthenticationSecret())) {
                throw new AuthenticationSecretNotProvidedException("Authentication secret not provided.");
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

    public void setMParticleClient(final MParticleClient mParticleClient) {
        this.mParticleClient = mParticleClient;
    }
}
