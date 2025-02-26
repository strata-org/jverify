package com.aws.jverify.verifier;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.nio.file.Path;

public record VerifierOptions(String dafnyPath,
                              Path libraryJar,
                              Path additionalDafnyFile,
                              @Nullable Path printDafny, 
                              Path printBinaryDafny, 
                              boolean noSnippets) {}
