package com.aws.jverify.common;

public class AnnotatedRange {
    public final String annotation;
    public final Range range;

    public AnnotatedRange(String annotation, Range range) {
        this.annotation = annotation;
        this.range = range;
    }
}
