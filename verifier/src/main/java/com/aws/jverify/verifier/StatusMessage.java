package com.aws.jverify.verifier;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StatusMessage extends DafnyOutput {
    @JsonProperty("value")
    String value;

    public StatusMessage(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
