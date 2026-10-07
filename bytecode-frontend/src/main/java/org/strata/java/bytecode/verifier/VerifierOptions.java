package org.strata.java.bytecode.verifier;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * Options for a verification run. Port of StrataJavaFrontEnd's
 * {@code org.strata.jverify.verifier.VerifierOptions} minus the
 * javac-frontend-only knobs (PositionFilter, backendPath/lake) and plus the
 * bytecode-frontend inputs:
 *
 * @param classPathEntries entries put on javac's classpath when compiling the
 *        input sources with {@code -g -Xjcov} (must include the bytecode-frontend spec library
 *        jar so spec calls/annotations resolve), and consulted when reading
 *        referenced classfiles.
 * @param contractSourcePath contract sources (directories of .java files or
 *        .jar files, resolved against workingDirectory) applied in addition
 *        to the always-available object-contract.java.
 * @param jvmVerifyClassfiles run the JVM bytecode-verification gate on the
 *        classfiles before translation, refusing any that fail
 *        {@code ClassFile.verify}. Off by default: the driver's only
 *        classfiles are its own javac output, which always verifies.
 */
public record VerifierOptions(PrintWriter outWriter,
                              Path workingDirectory,
                              Collection<Path> classPathEntries,
                              @Nullable Path printSerializedOutputProgram,
                              boolean emitLaurelOnly,
                              boolean showRanges,
                              Collection<Path> contractSourcePath,
                              boolean showFilepaths,
                              boolean verifyByDefault,
                              boolean continueOnErrors,
                              boolean verbose,
                              boolean shouldTrackTime,
                              boolean interpret,
                              @Nullable String entry,
                              @Nullable Integer fuel,
                              boolean jvmVerifyClassfiles) {
    public <T> T time(String name, Supplier<T> supply) {
        if (!shouldTrackTime) {
            return supply.get();
        }
        var before = Instant.now();
        var result = supply.get();
        var after = Instant.now();
        printTime(name, Duration.between(before, after));
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
        printTime(name, Duration.between(before, Instant.now()));
    }
}
