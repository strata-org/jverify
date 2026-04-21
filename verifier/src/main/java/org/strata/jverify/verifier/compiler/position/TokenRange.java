package org.strata.jverify.verifier.compiler.position;

import org.checkerframework.checker.nullness.qual.Nullable;

public record TokenRange(Token startToken, @Nullable Token endToken) {
}
