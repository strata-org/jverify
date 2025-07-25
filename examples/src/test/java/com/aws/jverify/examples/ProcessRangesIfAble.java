package com.aws.jverify.examples;

import com.aws.jverify.*;

import static com.aws.jverify.JVerify.callIfAble;
import static com.aws.jverify.JVerify.precondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

public class ProcessRangesIfAble {
    record Range(int start, int end, int value) {
        @Invariant
        boolean startBeforeEnd() {
            return start < end;
        }
    }

    public void execute(String input, boolean continueOnIncorrectRanges) {
        var ranges = new ArrayList<Range>();
        var succeeded = parse(input, ranges);
        if (succeeded || continueOnIncorrectRanges) {
            // Only do the call if the precondition of `processRanges` is met
            var processedRanges = callIfAble(() -> processRanges(ranges));
        }
    }

    private boolean parse(String input, List<Range> result) {
        // To simplify this example, we assume that input has correct syntax.
        
        var rangeStrings = input.split(";");
        for(var rangeString : rangeStrings) {
            var parts = rangeString.split(",");
            var start = Integer.parseInt(parts[0]);
            var end = Integer.parseInt(parts[1]);
            var value = Integer.parseInt(parts[2]);

            // Only construct a range if its invariant holds
            Optional<Range> range = callIfAble(() -> new Range(start, end, value));
            if (range.isPresent()) {
                result.add(range.get());
            } else {
                // Stop parsing because otherwise we'll have non-contiguous ranges 
                return false;
            }
        }
        return true;
    }

    void processRanges(List<Range> ranges) {
        // Ranges are continuous
        precondition(IntStream.range(0, ranges.size() - 1).allMatch(i ->
                ranges.get(i).end() == ranges.get(i + 1).start()));

        throw new RuntimeException("not yet implemented");
    }
}