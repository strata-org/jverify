package org.strata.java.bytecode.verifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.strata.jverify.verifier.DiagnosticWithRange;
import org.strata.jverify.verifier.JVerifyResults;
import org.strata.jverify.verifier.SourceFile;
import org.strata.java.bytecode.frontend.BytecodeFrontend;
import org.strata.java.bytecode.verifier.compiler.BytecodeToLaurelCompiler;
import org.strata.java.bytecode.verifier.compiler.Reporter;
import org.strata.java.bytecode.verifier.laurel.StrataBackend;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tiered-input invariant: the same program verifies to the SAME verdicts,
 * counts, and exit code whether compiled with {@code -g -Xjcov} (FULL),
 * {@code -g} (DEBUG), or default javac flags (BARE) -- only position
 * precision degrades (statement spans -> source lines). Input with no debug
 * information at all ({@code -g:none}) refuses up front.
 */
class TieredInputEquivalenceTest {

    private static final List<String> FULL = List.of("-g", "-Xjcov", "-proc:none");
    private static final List<String> DEBUG = List.of("-g", "-proc:none");
    private static final List<String> BARE = List.of("-proc:none");

    /**
     * Two correct methods (contracts + loop invariant), two failing ones
     * (a wrong check; an int32 overflow obligation), and an explicit
     * constructor (skipped, CRT-free detection must classify it too).
     */
    private static final String SOURCE = """
            import static org.strata.jverify.JVerify.*;

            public class Demo {
                int stored;

                Demo(int seed) {
                    stored = seed;
                }

                static int clamp(int lo, int hi, int x) {
                    precondition(lo <= hi);
                    int result;
                    if (x < lo) {
                        result = lo;
                    } else {
                        if (x > hi) {
                            result = hi;
                        } else {
                            result = x;
                        }
                    }
                    check(result >= lo);
                    check(result <= hi);
                    return result;
                }

                static int countTo(int n) {
                    precondition(n >= 0);
                    int i = 0;
                    while (i < n) {
                        invariant(i >= 0);
                        invariant(i <= n);
                        i = i + 1;
                    }
                    check(i == n);
                    return i;
                }

                static int doubleIt(int x) {
                    precondition(x >= 0);
                    precondition(x <= 1000);
                    int y = x + x;
                    check(y == 2 * x + 1);
                    return y;
                }

                int bump(int by) {
                    precondition(by >= 0);
                    stored = stored > 0 ? stored + by : by;
                    return stored;
                }

                // Non-static inner class: its ctor is compiler-synthesized
                // (enclosing-instance parameter + preamble) and must be
                // classified implicit -> Verified on every tier.
                class Helper {
                    int doubled(int v) {
                        precondition(v >= 0);
                        precondition(v <= 1000);
                        check(v + v >= v);
                        return v + v;
                    }
                }
            }
            """;

    private record Run(Reporter reporter, JVerifyResults results) {
    }

    private static Path libraryClasses() {
        return org.strata.java.bytecode.verifier.VerifierDriver.specLibraryLocation();
    }

    private static VerifierOptions options() {
        return new VerifierOptions(
                new PrintWriter(new StringWriter(), true),
                Path.of(System.getProperty("user.dir")),
                List.of(libraryClasses()),
                null, false, true, List.of(), false,
                true, false, false, false, false, null, null, false);
    }

    /** Compile SOURCE with the given javac flags, then translate; runs Strata unless the frontend refused. */
    private Run run(Path tmp, String dir, List<String> javacOptions) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var collector = new DiagnosticCollector<JavaFileObject>();
        Path classes = tmp.resolve(dir);
        Files.createDirectories(classes);
        var src = new SourceFile(tmp.resolve("Demo.java"), SOURCE);
        try (var fm = compiler.getStandardFileManager(collector, null, null)) {
            fm.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classes));
            fm.setLocationFromPaths(StandardLocation.CLASS_PATH, List.of(libraryClasses()));
            assertTrue(compiler.getTask(new PrintWriter(new StringWriter()), fm, collector,
                            javacOptions, null, List.of(src)).call(),
                    () -> "javac failed: " + collector.getDiagnostics());
        }
        var pool = BytecodeFrontend.readClasses(classes);
        var reporter = new Reporter();
        // The frontend gets the input URI only (paths, no source text).
        var analysis = new BytecodeToLaurelCompiler(reporter, true, List.of(libraryClasses()))
                .analyze(List.of(src.toUri()), pool);
        if (reporter.hasErrors()) {
            return new Run(reporter, null);
        }
        var results = new StrataBackend(options())
                .verifyProgram(analysis.program(), analysis.positions(), analysis.methodStatuses());
        return new Run(reporter, results);
    }

    /** The 1-indexed source lines of the two planted verification failures. */
    private static List<Integer> expectedFailureLines() {
        var lines = SOURCE.split("\n", -1);
        Integer check = null, overflow = null;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("check(y == 2 * x + 1)")) {
                check = i + 1;
            }
            if (lines[i].contains("stored = stored > 0")) {
                overflow = i + 1;
            }
        }
        return List.of(java.util.Objects.requireNonNull(check), java.util.Objects.requireNonNull(overflow));
    }

    private static List<Integer> diagnosticLines(JVerifyResults results) {
        return results.diagnostics().stream()
                .map(d -> ((DiagnosticWithRange) d).getRange().start().line())
                .sorted()
                .toList();
    }

    private static List<Integer> diagnosticColumns(JVerifyResults results) {
        return results.diagnostics().stream()
                .map(d -> ((DiagnosticWithRange) d).getRange().start().character())
                .sorted()
                .toList();
    }

    @Test
    void fullTierBaseline(@TempDir Path tmp) throws Exception {
        var run = run(tmp, "full", FULL);
        assertEquals(List.of(), run.reporter().diagnostics(), "FULL is silent about its tier");
        var ran = assertInstanceOf(JVerifyResults.Ran.class, run.results());
        assertEquals(4, ran.exitCode());
        assertEquals(expectedFailureLines(), diagnosticLines(ran),
                () -> ran.diagnostics().stream().map(d -> d.getMessage(null)).toList().toString());
        // FULL carries COLUMN precision (CRT begin column): both failures sit
        // on indented statements, so their start column is past column 1.
        assertTrue(diagnosticColumns(ran).stream().allMatch(c -> c > 1),
                () -> "FULL should carry column precision, got columns " + diagnosticColumns(ran));
        // clamp, countTo, Helper.doubled, Helper's implicit (synthesized) ctor, and the explicit ctor.
        assertEquals(5, ran.verificationResults().verificationPassedMethods());
        assertEquals(2, ran.verificationResults().verificationFailedMethods());   // doubleIt, bump
        assertEquals(0, ran.verificationResults().verificationSkippedMethods());
    }

    @Test
    void debugTierIsVerdictEquivalent(@TempDir Path tmp) throws Exception {
        assertTierEquivalent(tmp, DEBUG, "debug", "-Xjcov");
    }

    @Test
    void bareTierIsVerdictEquivalent(@TempDir Path tmp) throws Exception {
        assertTierEquivalent(tmp, BARE, "bare", "-g");
    }

    private void assertTierEquivalent(Path tmp, List<String> flags, String dir, String warnedFlag)
            throws Exception {
        var full = run(tmp, "full", FULL);
        var degraded = run(tmp, dir, flags);

        assertEquals(1, degraded.reporter().diagnostics().size(),
                () -> "one tier warning expected, got: " + degraded.reporter().diagnostics());
        var warning = degraded.reporter().diagnostics().getFirst().getMessage(null);
        assertTrue(warning.contains("input compiled without " + warnedFlag),
                () -> "tier warning should name the missing flag: " + warning);

        var fullRan = assertInstanceOf(JVerifyResults.Ran.class, full.results());
        var degradedRan = assertInstanceOf(JVerifyResults.Ran.class, degraded.results());

        // The fail-safe invariant: verdicts, counts, and exit code identical.
        assertEquals(fullRan.exitCode(), degradedRan.exitCode());
        assertEquals(fullRan.verificationResults().verificationPassedMethods(),
                degradedRan.verificationResults().verificationPassedMethods());
        assertEquals(fullRan.verificationResults().verificationFailedMethods(),
                degradedRan.verificationResults().verificationFailedMethods());
        assertEquals(fullRan.verificationResults().verificationSkippedMethods(),
                degradedRan.verificationResults().verificationSkippedMethods());
        assertEquals(fullRan.verificationResults().verificationFailedAssertions(),
                degradedRan.verificationResults().verificationFailedAssertions());

        // Positions degrade to LINE precision, never further: same lines as
        // FULL, but the column collapses to 1 (LineNumberTable has no column).
        assertEquals(diagnosticLines(fullRan), diagnosticLines(degradedRan));
        assertTrue(diagnosticColumns(degradedRan).stream().allMatch(c -> c == 1),
                () -> "DEBUG/BARE should carry line precision only (column 1), got "
                        + diagnosticColumns(degradedRan));
    }

    @Test
    void strippedInputRefuses(@TempDir Path tmp) throws Exception {
        // Self-enforcing: analyze() throws, so even a caller that never
        // checks the reporter cannot verify a refused input.
        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
                org.strata.java.bytecode.verifier.compiler.RefusedInputException.class,
                () -> run(tmp, "none", List.of("-g:none", "-proc:none")));
        assertTrue(thrown.getMessage().contains("no debug information"), thrown.getMessage());
    }
}
