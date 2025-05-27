package com.aws.jverify.verifier;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StatusMessage.class, name = "status"),
        @JsonSubTypes.Type(value = DafnyDiagnostic.class, name = "diagnostic")
})

public abstract class DafnyOutput {
}
