package org.strata.jverify.examples;

import org.strata.jverify.CheckPreconditionsAtRuntime;
import org.strata.jverify.Invariant;
import org.strata.jverify.PreconditionFailure;
import org.strata.jverify.Verify;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

public class ValidRangesJacksonFineGrained {

    @CheckPreconditionsAtRuntime
    record Range(int start, int end, int value) {
        @Invariant
        boolean startBeforeEnd() {
            return start < end;
        }
    }

    record ParseResult(boolean succeeded, List<Range> ranges) {}

    @Verify(false)
    @JsonDeserialize(using = RangeListDeserializer.class)
    ParseResult parse(String json) throws JacksonException {
        ObjectMapper mapper = JsonMapper.builder().build();
        List<Range> ranges = mapper.readValue(json, new TypeReference<>() {});
        if (ranges.getLast() == null) {
            ranges.removeLast();
            return new ParseResult(false, ranges);
        } else {
            return new ParseResult(true, ranges);
        }
    }

    static class RangeListDeserializer extends ValueDeserializer<List<Range>> {
        private static final ObjectMapper DEFAULT_MAPPER = JsonMapper.builder().build();

        @Override
        public List<Range> deserialize(JsonParser p, DeserializationContext ctxt) {
            JsonNode arrayNode = ctxt.readTree(p);
            List<Range> ranges = new ArrayList<>();

            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode node = arrayNode.get(i);

                try {
                    Range range = DEFAULT_MAPPER.treeToValue(node, Range.class);
                    ranges.add(range);
                } catch (PreconditionFailure e) {
                    ranges.add(null);
                    break;
                }
            }

            return ranges;
        }
    }
}
