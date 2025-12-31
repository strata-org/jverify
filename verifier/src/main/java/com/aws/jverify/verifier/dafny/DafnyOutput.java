package com.aws.jverify.verifier.dafny;

import com.aws.jverify.verifier.DafnyDiagnostic;
import com.aws.jverify.verifier.StatusMessage;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public sealed abstract class DafnyOutput permits DafnyDiagnostic, StatusMessage {
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