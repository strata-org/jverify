package com.aws.jverify.common;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public record Position(int line, int character) {

    @Override
    public String toString() {
        CompletableFuture future;
        return (line + 1) + ":" + (character + 1);
    }
}
