package com.aws.jverify.verifier;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.function.Supplier;

public record VerifierOptions(Writer outWriter,
                              Path workingDirectory,
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
                              @Nullable PositionFilter positionFilter,
                              boolean verbose,
                              boolean shouldTrackTime) {
    public <T> T time(String name, Supplier<T> supply) {
        if (!shouldTrackTime) {
            return supply.get();
        }
        var before = Instant.now();
        var result = supply.get();
        var after = Instant.now();
        var duration = Duration.between(before, after);
        printTime(name, duration);
        return result;
    }

    public void printTime(String name, Duration duration) {
        try {
            outWriter.write(name + " took " + duration.toMillis() + " ms\n");
        } catch (IOException _) {
        }
    }

    public void time(String name, Runnable runnable) {
        if (!shouldTrackTime) {
            runnable.run();
            return;
        }
        var before = Instant.now();
        runnable.run();
        var after = Instant.now();
        var duration = Duration.between(before, after);
        printTime(name, duration);
    }
}
