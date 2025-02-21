package com.aws.jverify;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.nio.file.Path;

public record VerifierOptions(@Nullable Path printDafny, Path printBinaryDafny) {}
