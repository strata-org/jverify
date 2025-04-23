package com.aws.jverify.common;

public record AnnotatedRange(String annotation, Range range) implements Comparable<AnnotatedRange> {
    @Override
    public int compareTo(AnnotatedRange o) {
        var rangeSign = range.compareTo(o.range);
        return rangeSign == 0 ? annotation.compareTo(o.annotation) : rangeSign;
    }
}
