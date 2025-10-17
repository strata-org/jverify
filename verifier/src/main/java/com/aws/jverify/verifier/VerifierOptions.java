package com.aws.jverify.verifier;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.nio.file.Path;
import java.util.Collection;

public record VerifierOptions(Path workingDirectory,
                              Path dafnyPath,
                              Collection<Path> extraClassPathEntries,
                              Path additionalDafnyFile,
                              boolean testDafnyVersion,
                              @Nullable Path printDafny,
                              @Nullable Path printSimplifiedJava,
                              @Nullable Path printBinaryDafny, 
                              boolean showRanges,
                              boolean includeBuiltinContracts,
                              boolean showFilepaths,
                              String[] additionalDafnyArguments,
                              boolean verifyByDefault,
                              boolean continueOnErrors,
                              @Nullable PositionFilter positionFilter,
                              boolean verbose) {
}
