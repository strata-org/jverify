package com.aws.jverify.verifier;

import com.aws.jverify.verifier.compiler.simplifications.VerifyAnnotationCompiler;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.nio.file.Path;
import java.util.Collection;

public record VerifierOptions(Path workingDirectory,
                              Path dafnyPath,
                              Collection<Path> extraClassPathEntries,
                              Path additionalDafnyFile,
                              boolean testDafnyVersion,
                              @Nullable Path printDafny, 
                              Path printBinaryDafny, 
                              boolean showRanges,
                              boolean includeBuiltinContracts,
                              boolean showFilepaths,
                              String[] additionalDafnyArguments,
                              boolean verifyByDefault,
                              boolean continueOnErrors,
                              @Nullable PositionFilter positionFilter) {
}
