package com.aws.jverify.verifier;

import com.aws.jverify.verifier.dafny.DafnyOutput;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class StatusMessage extends DafnyOutput {
    @JsonProperty("value")
    String value;

    public StatusMessage(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
