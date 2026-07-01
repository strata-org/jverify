package org.strata.jverify.verifier;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.function.Supplier;

public record VerifierOptions(PrintWriter outWriter,
                              Path workingDirectory,
                              Path backendPath,
                              Collection<Path> extraClassPathEntries,
                              @Nullable Path printSerializedOutputProgram,
                              boolean emitLaurelOnly,
                              boolean showRanges,
                              Collection<Path> contractSourcePath,
                              boolean showFilepaths,
                              boolean verifyByDefault,
                              boolean continueOnErrors,
                              @Nullable PositionFilter positionFilter,
                              boolean verbose,
                              boolean shouldTrackTime,
                              @Nullable Path keepAllFilesDir) {
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
        if (!shouldTrackTime) {
            return;
        }
        outWriter.println(name + " took " + duration.toMillis() + " ms");
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
