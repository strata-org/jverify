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

