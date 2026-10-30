package org.strata.java.bytecode.verifier.laurel;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Test;
import org.strata.java.bytecode.verifier.VerifierOptions;

import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The Strata command line and directory {@link StrataBackend} runs, without running it. */
class StrataInvocationTest {

    private static VerifierOptions options(boolean interpret, @Nullable String entry, @Nullable Integer fuel,
                                           @Nullable String interpreter, @Nullable Path strataProjectDir) {
        return new VerifierOptions(new PrintWriter(Writer.nullWriter()), Path.of("."), List.of(), null, false,
                false, List.of(), false, true, false, false, false, interpret, entry, fuel, false, interpreter,
                strataProjectDir, false);
    }

    @Test
    void verificationRunsStrataFromPath() {
        var invocation = StrataBackend.Invocation.of(options(false, null, null, null, null));
        assertEquals(List.of("strataLaurel", "laurelAnalyzeBinary", "--solver", "z3",
                "--skip-phases", "CoroutineElaboration,YieldElim"),
                invocation.command());
        assertNull(invocation.directory());
        assertEquals(invocation.command(), invocation.processBuilder().command());
        assertNull(invocation.processBuilder().directory());
    }

    @Test
    void interpretationPassesEntryFuelAndInterpreter() {
        var invocation = StrataBackend.Invocation.of(options(true, "p.C#m", 7, "laurel", null));
        assertEquals(List.of("strataLaurel", "laurelInterpretBinary", "--entry", "p.C::m", "--fuel", "7",
                "--interpreter", "laurel"), invocation.command());
        assertNull(invocation.directory());
        // No explicit --fuel and not the Laurel interpreter: the frontend
        // passes its own 100000 default (checked arithmetic costs ~3x the
        // Core interpreter's 10000-per-call budget).
        assertEquals(List.of("strataLaurel", "laurelInterpretBinary", "--fuel", "100000",
                "--skip-phases", "CoroutineElaboration,YieldElim"),
                StrataBackend.Invocation.of(options(true, null, null, null, null)).command());
    }

    @Test
    void aStrataProjectDirectoryRunsLakeExeInItsStrataCli() {
        var project = Path.of("/work/Strata");
        var interpret = StrataBackend.Invocation.of(options(true, "p.C#m", null, "core", project));
        assertEquals(List.of("lake", "exe", "-q", "strata", "laurelInterpretBinary", "--entry", "p.C::m",
                "--fuel", "100000", "--interpreter", "core",
                "--skip-phases", "CoroutineElaboration,YieldElim"),
                interpret.command());
        assertEquals(project.resolve("StrataCLI"), interpret.directory());
        assertEquals(interpret.command(), interpret.processBuilder().command());
        assertEquals(project.resolve("StrataCLI").toFile(), interpret.processBuilder().directory());

        var verify = StrataBackend.Invocation.of(options(false, null, null, null, project));
        assertEquals(List.of("lake", "exe", "-q", "strata", "laurelAnalyzeBinary", "--solver", "z3",
                "--skip-phases", "CoroutineElaboration,YieldElim"),
                verify.command());
        assertEquals(project.resolve("StrataCLI"), verify.directory());
    }

    @Test
    void aLaunchFailureNamesWhatFailedToStart() {
        assertEquals("`strataLaurel` from PATH (pass --strata <path> or set JVERIFY_STRATA)",
                StrataBackend.Invocation.of(options(false, null, null, null, null)).describe());
        assertEquals("`lake exe -q strata` in " + Path.of("/work/Strata/StrataCLI"),
                StrataBackend.Invocation.of(options(false, null, null, null, Path.of("/work/Strata"))).describe());
    }

    @Test
    void cortexWithProjectDirIsRejected() {
        System.setProperty("jverify.cortex", "true");
        try {
            assertThrows(IllegalArgumentException.class, () ->
                    StrataBackend.Invocation.of(options(false, null, null, null, Path.of("/work/Strata"))));
        } finally {
            System.clearProperty("jverify.cortex");
        }
    }
}
