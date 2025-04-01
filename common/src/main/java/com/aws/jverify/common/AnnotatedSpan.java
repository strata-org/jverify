package com.aws.jverify.common;

public class AnnotatedSpan {
    public final String annotation;
    public final TextSpan span;

    public AnnotatedSpan(String annotation, TextSpan span) {
        this.annotation = annotation;
        this.span = span;
    }
}
