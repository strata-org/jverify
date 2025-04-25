package com.aws.jverify.verifier;

import java.nio.file.Path;
import org.checkerframework.checker.nullness.qual.Nullable;

public record VerifierOptions(
        Path dafnyPath,
        Path libraryJar,
        Path additionalDafnyFile,
        @Nullable Path printDafny,
        Path printBinaryDafny,
        boolean showRanges,
        String[] additionalDafnyArguments) {}
