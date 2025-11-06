package com.aws.jverify.verifier.compiler.simplifications;

public interface Property<T> {
    T get();
    void set(T value);
}
