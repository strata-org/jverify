package com.aws.jverify.verifier;

import com.fasterxml.jackson.annotation.JsonCreator;

public class StatusMessage extends DafnyOutput {
    String value;
    
    @JsonCreator
    public StatusMessage(String value) {
        this.value = value;
    }
}
