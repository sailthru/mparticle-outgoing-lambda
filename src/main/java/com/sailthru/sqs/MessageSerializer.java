package com.sailthru.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public final class MessageSerializer {
    private final ObjectMapper mapper = new ObjectMapper();

    public <T> T deserialize(String content, Class<T> valueType) throws IOException {
        return this.mapper.readValue(content, valueType);
    }

    public byte[] serializeToBytes(Object newMessage) throws JsonProcessingException {
        return this.mapper.writeValueAsBytes(newMessage);
    }

    public String serializeToString(Object newMessage) throws JsonProcessingException {
        return this.mapper.writeValueAsString(newMessage);
    }
}
