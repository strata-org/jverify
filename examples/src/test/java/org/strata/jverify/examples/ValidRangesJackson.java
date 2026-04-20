package org.strata.jverify.examples;

import org.strata.jverify.CheckPreconditionsAtRuntime;
import org.strata.jverify.Invariant;
import org.strata.jverify.PreconditionFailure;
import org.strata.jverify.Verify;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
