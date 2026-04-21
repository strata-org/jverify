package org.strata.jverify.examples;

import org.strata.jverify.CheckPreconditionsAtRuntime;
import org.strata.jverify.Invariant;
import org.strata.jverify.PreconditionFailure;
import org.strata.jverify.Verify;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
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
    ParseResult parse(String json) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        List<Range> ranges = mapper.readValue(json, new TypeReference<>() {});
        if (ranges.getLast() == null) {
            ranges.removeLast();
            return new ParseResult(false, ranges);
        } else {
            return new ParseResult(true, ranges);
        }
    }
    
    static class RangeListDeserializer extends JsonDeserializer<List<Range>> {
        private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper();
        
        @Override
        public List<Range> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode arrayNode = p.getCodec().readTree(p);
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
