package com.aws.jverify.verifier.compiler.position;

import org.checkerframework.checker.nullness.qual.Nullable;

public record TokenRange(Token startToken, @Nullable Token endToken) {
    public Token getStartToken() { return startToken; }
    public Token getEndToken() { return endToken; }
}
