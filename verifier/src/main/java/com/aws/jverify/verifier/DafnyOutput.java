package com.aws.jverify.verifier;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public abstract class DafnyOutput {
}

class DafnyOutputDeserializer extends JsonDeserializer<DafnyOutput> {
    private final ObjectMapper mapper;

    public DafnyOutputDeserializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public DafnyOutput deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        String type = node.get("type").asText();
        JsonNode valueNode = node.get("value");

        return switch (type) {
            case "diagnostic" -> mapper.treeToValue(valueNode, DafnyDiagnostic.class);
            case "status" -> new StatusMessage(valueNode.asText());
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }
}
