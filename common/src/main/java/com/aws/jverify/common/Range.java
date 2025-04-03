package com.aws.jverify.common;

public record Range(Position start, Position end) {

    @Override
    public String toString() {
        return start.toString() + "-" + start.toString();
    }
}
