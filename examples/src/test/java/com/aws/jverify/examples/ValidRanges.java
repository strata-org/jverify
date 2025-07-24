package com.aws.jverify.examples;

import com.aws.jverify.CheckPreconditionsAtRuntime;
import com.aws.jverify.Invariant;
import net.jqwik.api.constraints.IntRange;

import java.util.List;
import java.util.stream.IntStream;

import static com.aws.jverify.JVerify.precondition;

@CheckPreconditionsAtRuntime
record Range(int start, int end, int value) {
    @Invariant
    boolean startBeforeEnd() {
        return start < end;
    }
}

public class ValidRanges {
    @CheckPreconditionsAtRuntime
    public void ProcessRanges(List<Range> ranges) {
        // Ranges are continuous
        precondition(IntStream.range(0, ranges.size() - 1).allMatch(i -> 
                ranges.get(i).end() == ranges.get(i + 1).start()));
        
        // process ranges
    }
}
