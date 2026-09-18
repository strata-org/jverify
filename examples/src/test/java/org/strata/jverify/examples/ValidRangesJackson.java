package org.strata.jverify.examples;

import org.strata.jverify.CheckPreconditionsAtRuntime;
import org.strata.jverify.Invariant;
import org.strata.jverify.PreconditionFailure;
import org.strata.jverify.Verify;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
    List<Range> parse(String json) throws JacksonException {
        ObjectMapper mapper = JsonMapper.builder().build();
        try {
            // Checks that for every range, 'start < end'
            return mapper.readValue(json, new TypeReference<>() {});
        } catch(PreconditionFailure _) {
            System.out.println("Given JSON had faulty data");
            return List.of();
        }
    }
}
