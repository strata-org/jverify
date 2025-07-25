package com.aws.jverify.examples;

import com.aws.jverify.CheckPreconditionsAtRuntime;
import com.aws.jverify.Invariant;
import com.aws.jverify.PreconditionFailure;
import com.aws.jverify.Verify;
import org.gradle.internal.impldep.com.fasterxml.jackson.core.JsonProcessingException;
import org.gradle.internal.impldep.com.fasterxml.jackson.core.type.TypeReference;
import org.gradle.internal.impldep.com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public class ValidRangesJackson {

    @CheckPreconditionsAtRuntime
    record Range(int start, int end, int value) {
        @Invariant
        boolean startBeforeEnd() {
            return start < end;
        }
    }

    @Verify(false)
    List<Range> parse(String json) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        try {
            // Checks that for every range, 'start < end'
            return mapper.readValue(json, new TypeReference<>() {});
        } catch(PreconditionFailure _) {
            System.out.println("Given JSON had faulty data");
            return List.of();
        }
    }
}
