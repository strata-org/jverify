package com.aws.jverify;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.nio.file.Path;

public record VerifierOptions(String dafnyPath,
                              @Nullable Path printDafny, 
                              Path printBinaryDafny, 
                              boolean noSnippets) {}
