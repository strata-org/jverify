package com.aws.jverify.common;

public record Position(int line, int character) {

    @Override
    public String toString() {
        return (line + 1) + ":" + (character + 1);
    }
}
