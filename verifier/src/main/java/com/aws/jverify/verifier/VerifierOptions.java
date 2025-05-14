package com.aws.jverify.verifier;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.nio.file.Path;
import java.util.Collection;

public record VerifierOptions(Path dafnyPath,
                              Path libraryJar,
                              Collection<Path> extraClassPathEntries,
                              Path additionalDafnyFile,
                              @Nullable Path printDafny, 
                              Path printBinaryDafny, 
                              boolean showRanges,
                              String[] additionalDafnyArguments,
                              boolean verifyByDefault) {}
