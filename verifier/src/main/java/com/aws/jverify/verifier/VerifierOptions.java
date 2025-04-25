package com.aws.jverify.verifier;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.nio.file.Path;

public record VerifierOptions(Path dafnyPath,
                              Path libraryJar,
                              Path additionalDafnyFile,
                              @Nullable Path printDafny, 
                              Path printBinaryDafny, 
                              boolean showRanges,
                              String[] additionalDafnyArguments) {}
