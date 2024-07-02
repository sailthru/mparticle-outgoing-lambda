package com.sailthru.sqs;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.sailthru.sqs.exception.RetryLaterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.utils.StringUtils;

import java.io.IOException;

public class MessageProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageProcessor.class);

    private MessageSerializer messageSerializer;
    private MParticleClient mParticleClient;

    public void process(final SQSEvent.SQSMessage sqsMessage) throws RetryLaterException {
        final String rawMessage = sqsMessage.getBody();
        LOGGER.debug("Received message: {}", rawMessage);

        final MParticleMessage message = parseAndValidateMessage(rawMessage);

        getMParticleClient().submit(message);
    }

    private MParticleMessage parseAndValidateMessage(final String rawMessage) {
        try {
            final MParticleMessage message = getSerializer().deserialize(rawMessage, MParticleMessage.class);

            if (StringUtils.isEmpty(message.getAuthenticationKey())) {
                throw new IllegalArgumentException("Authentication key not provided.");
            }

            if (StringUtils.isEmpty(message.getAuthenticationSecret())) {
                throw new IllegalArgumentException("Authentication secret not provided.");
            }

            return message;
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Could not deserialize message: %s", rawMessage), e);
        }
    }

    private MParticleClient getMParticleClient() {
        if (mParticleClient == null) {
            mParticleClient = new MParticleClient();
        }

        return mParticleClient;
    }

    private MessageSerializer getSerializer() {
        if (messageSerializer == null) {
            messageSerializer = new MessageSerializer();
        }

        return messageSerializer;
    }

    public void setMParticleClient(final MParticleClient mParticleClient) {
        this.mParticleClient = mParticleClient;
    }
}
