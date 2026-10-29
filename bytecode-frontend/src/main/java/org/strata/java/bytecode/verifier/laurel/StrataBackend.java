package org.strata.java.bytecode.verifier.laurel;

import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonBinaryWriterBuilder;
import com.amazon.ion.system.IonSystemBuilder;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.strata.jverify.common.Position;
import org.strata.jverify.common.Range;
import org.strata.jverify.laurel.LaurelProgram;
import org.strata.jverify.verifier.IntervalTree;
import org.strata.jverify.verifier.JVerifyResults;
import org.strata.jverify.verifier.StrataTimeout;
import org.strata.java.bytecode.verifier.MethodVerificationStatus;
import org.strata.jverify.verifier.VerificationResults;
import org.strata.java.bytecode.verifier.VerifierOptions;
import org.strata.jverify.verifier.Wrapper;
import org.strata.jverify.verifier.laurel.StrataOutput;

import javax.tools.Diagnostic;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serializes a Laurel {@link Program} to binary Ion, runs the Strata CLI on
 * it, and parses the resulting diagnostics. The caller supplies the Laurel
 * program, the offset-to-position {@link PositionResolver}, and the per-URI
 * method status trees.
 */
public class StrataBackend {

    static final List<String> UNUSED_LAUREL_PHASES = List.of(
            "CoroutineElaboration", "YieldElim");

    private final VerifierOptions verifierOptions;

    public StrataBackend(VerifierOptions verifierOptions) {
        this.verifierOptions = verifierOptions;
    }

    /**
     * Refuse an {@code entry} that names no static no-argument procedure;
     * otherwise serialize {@code program} to Ion, optionally write it to
     * {@code printSerializedOutputProgram}, and (unless emit-Laurel-only mode
     * is on) run the Strata backend on it.
     *
     * @param methodStatuses per-file interval trees of method positions,
     *        keyed by starting line; mutated in place: methods whose range
     *        contains a diagnostic are marked Failed, and when Strata stops
     *        before generating verification conditions the remaining
     *        Verified methods are marked Skipped.
     */
    public JVerifyResults verifyProgram(LaurelProgram program,
                                        PositionResolver positions,
                                        Map<URI, IntervalTree<Integer, MethodVerificationStatus>> methodStatuses) {
        if (verifierOptions.interpret() && verifierOptions.entry() != null
                && program.staticProcedures().stream().noneMatch(p -> p.inputs().isEmpty()
                        && p.name().text().equals(entryProcedure(verifierOptions.entry())))) {
            verifierOptions.outWriter().println("--entry " + verifierOptions.entry()
                    + " does not name a static no-argument method");
            return new JVerifyResults.NoVerdict(new ArrayList<>(), 2);
        }
        var serializedProgram = verifierOptions.time("Serializing Laurel AST", () -> {
            var ion = IonSystemBuilder.standard().build();
            var programIon = program.toIon(ion);

            if (verifierOptions.printSerializedOutputProgram() != null) {
                try {
                    var outPath = verifierOptions.printSerializedOutputProgram();
                    if (outPath.getParent() != null) {
                        Files.createDirectories(outPath.getParent());
                    }
                    // Binary Ion so the emitted program can be consumed
                    // directly by Strata's binary Ion reader.
                    try (var out = Files.newOutputStream(outPath);
                         var writer = IonBinaryWriterBuilder.standard().build(out)) {
                        programIon.writeTo(writer);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return programIon;
        });

        if (verifierOptions.emitLaurelOnly()) {
            return new JVerifyResults.NoVerdict(new ArrayList<>(), 0);
        }

        return runVerifier(positions, methodStatuses, serializedProgram);
    }

    /** The Laurel procedure that the user-facing {@code pkg.Class#method} names: {@code pkg.Class_method}. */
    static String entryProcedure(String entry) {
        return entry.replace('#', '_');
    }

    /**
     * How Strata runs on a serialized program: the launcher that starts it, its
     * arguments, and the directory it runs in (null: the current one).
     */
    record Invocation(List<String> launcher, List<String> arguments, @Nullable Path directory) {
        /**
         * Without a Strata project directory, {@code strataLaurel} from PATH. Under
         * Brazil, Strata is a build-tool: its {@code runtime/bin/} is prepended
         * to PATH by {@code brazil-build}, and that PATH is inherited by Gradle
         * and by each Test task's forked JVM. With one, {@code lake exe -q strata}
         * in its StrataCLI subpackage, where the executable lives.
         */
        static Invocation of(VerifierOptions options) {
            var arguments = new ArrayList<String>();
            if (options.interpret()) {
                // Interpret mode shares the `==== DIAGNOSTICS ====` output format
                // with laurelAnalyzeBinary, so parseStrataOutput is reused as is.
                arguments.add("laurelInterpretBinary");
                if (options.entry() != null) {
                    arguments.add("--entry");
                    arguments.add(entryProcedure(options.entry()));
                }
                if (options.fuel() != null) {
                    arguments.add("--fuel");
                    arguments.add(Integer.toString(options.fuel()));
                }
                if (options.interpreter() != null) {
                    arguments.add("--interpreter");
                    arguments.add(options.interpreter());
                }
            } else {
                arguments.add("laurelAnalyzeBinary");
                arguments.add("--solver");
                arguments.add("z3");
            }
            if (!"laurel".equals(options.interpreter())) {
                arguments.add("--skip-phases");
                arguments.add(String.join(",", UNUSED_LAUREL_PHASES));
            }
            var projectDir = options.strataProjectDir();
            return projectDir == null
                    ? new Invocation(List.of("strataLaurel"), List.copyOf(arguments), null)
                    : new Invocation(List.of("lake", "exe", "-q", "strata"), List.copyOf(arguments),
                            projectDir.resolve("StrataCLI"));
        }

        List<String> command() {
            var command = new ArrayList<>(launcher);
            command.addAll(arguments);
            return command;
        }

        ProcessBuilder processBuilder() {
            var builder = new ProcessBuilder(command());
            if (directory != null) {
                builder.directory(directory.toFile());
            }
            return builder;
        }

        /** What failed to start, for the message. */
        String describe() {
            var launched = "`" + String.join(" ", launcher) + "`";
            return directory == null
                    ? launched + " from PATH (pass --strata <path> or set JVERIFY_STRATA)"
                    : launched + " in " + directory;
        }
    }

    public JVerifyResults runVerifier(PositionResolver positions,
                                      Map<URI, IntervalTree<Integer, MethodVerificationStatus>> methodStatuses,
                                      IonValue serializedProgram) {
        var invocation = Invocation.of(verifierOptions);
        var processBuilder = invocation.processBuilder();
        var abortUri = methodStatuses.keySet().stream().findFirst()
                .orElse(Paths.get("strata").toUri());
        return verifierOptions.time("Running Strata", () -> {
            StrataTimeout timeout = null;
            try (var process = new AutoClosingProcessWrapper(processBuilder.redirectErrorStream(true).start())) {
                timeout = StrataTimeout.watch(process.getProcess());
                try (var strataStdin = process.getProcess().getOutputStream();
                     var writer = IonBinaryWriterBuilder.standard().build(strataStdin)) {
                    serializedProgram.writeTo(writer);
                }
                var results = parseStrataOutput(positions, methodStatuses, verifierOptions, process.getProcess());
                return timeout.timedOut() ? timedOut(results, timeout, abortUri) : results;
            } catch (InterruptedException | IOException e) {
                verifierOptions.outWriter().println("Failed to run " + invocation.describe()
                        + "\nError message: " + e.getMessage());
                // Backend failed to launch: not a verification outcome.
                var notRun = new JVerifyResults.DidNotRun(new ArrayList<>(), -1, -1);
                return timeout != null && timeout.timedOut() ? timedOut(notRun, timeout, abortUri) : notRun;
            }
        });
    }

    private JVerifyResults timedOut(JVerifyResults results, StrataTimeout timeout, URI abortUri) {
        verifierOptions.outWriter().println(timeout.reason() + "; killed it");
        var pos = new Position(1, 1);
        var diagnostics = new ArrayList<Diagnostic<?>>(results.diagnostics());
        diagnostics.add(new StrataDiagnostic(abortUri, new Range(pos, pos), timeout.reason()));
        var strataExitCode = results instanceof JVerifyResults.DidNotRun d ? d.strataExitCode() : 0;
        return new JVerifyResults.DidNotRun(diagnostics, results.exitCode(), strataExitCode);
    }

    static class AutoClosingProcessWrapper implements AutoCloseable {
        private final Process process;

        AutoClosingProcessWrapper(Process process) {
            this.process = process;
        }

        Process getProcess() {
            return process;
        }

        @Override
        public void close() {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private JVerifyResults parseStrataOutput(PositionResolver positions,
                                             Map<URI, IntervalTree<Integer, MethodVerificationStatus>> methodStatuses,
                                             VerifierOptions options,
                                             Process process) throws IOException, InterruptedException {

        var diagnostics = new ArrayList<Diagnostic<?>>();
        try (var strataOutput = process.inputReader()) {
            Wrapper<Integer> failedAssertionsCount = new Wrapper<>(0);

            int verifiedCount = 0;
            int skippedCount = 0;
            int failedCount = 0;
            int unattributedErrorCount = 0;

            String line;
            boolean inDiagnosticsSection = false;
            StringBuilder preDiagnosticOutput = new StringBuilder();

            while ((line = strataOutput.readLine()) != null) {
                if (options.verbose()) {
                    options.outWriter().println(line);
                }
                if (!inDiagnosticsSection) {
                    preDiagnosticOutput.append(line).append("\n");
                }
                if (line.equals("==== DIAGNOSTICS ====")) {
                    inDiagnosticsSection = true;
                    continue;
                }

                if (inDiagnosticsSection) {
                    // A synthetic URI that never appears in methodStatuses: a
                    // malformed diagnostic carries no real location, so it
                    // must not be attributed to any method.
                    var fallbackUri = Paths.get("strata-unattributed").toUri();
                    var diagnostic = parseDiagnosticLine(line, positions, fallbackUri);
                    if (diagnostic == null) {
                        continue;
                    }
                    diagnostics.add(diagnostic);
                    failedAssertionsCount.setValue(failedAssertionsCount.getValue() + 1);

                    var uriMethods = methodStatuses.get(diagnostic.getUri());
                    var failedJavaMethod = uriMethods == null
                            ? null
                            : uriMethods.findInnermostAtPoint(diagnostic.getRange().start().line());
                    if (failedJavaMethod != null) {
                        failedJavaMethod.setVerificationStatus(MethodVerificationStatus.VerificationStatus.Failed);
                    } else {
                        unattributedErrorCount++;
                    }
                }
            }

            boolean haltedBeforeVerification = !options.interpret()
                    && !StrataOutput.reachedVerificationConditions(preDiagnosticOutput.toString());
            int haltedUnverifiedCount = 0;
            for (IntervalTree<Integer, MethodVerificationStatus> uriStatuses : methodStatuses.values()) {
                for (var methodStatus : uriStatuses.streamNodes().toList()) {
                    var status = methodStatus.getValue();
                    if (haltedBeforeVerification
                            && status.getVerificationStatus() == MethodVerificationStatus.VerificationStatus.Verified) {
                        status.setVerificationStatus(MethodVerificationStatus.VerificationStatus.Skipped);
                        haltedUnverifiedCount++;
                    }
                    switch (status.getVerificationStatus()) {
                        case Verified -> verifiedCount++;
                        case Skipped -> skippedCount++;
                        case Failed -> failedCount++;
                    }
                }
            }

            // Honesty in the summary: an error that could not be attributed
            // to any method leaves the per-method counts untouched; say so.
            if (unattributedErrorCount > 0) {
                options.outWriter().println("Note: " + unattributedErrorCount
                        + " error(s) could not be attributed to a specific method;"
                        + " the method counts below do not reflect them.");
            }

            if (haltedUnverifiedCount > 0) {
                options.outWriter().println("Note: Strata stopped before generating verification conditions;"
                        + " " + haltedUnverifiedCount + " method(s) without a diagnostic were not verified"
                        + " and are counted as skipped.");
            }

            int verifierExitCode = process.waitFor();
            if (verifierExitCode != 0) {
                options.outWriter().println("Strata exited with code " + verifierExitCode + ":\n" + preDiagnosticOutput);
            }
            var abortUri = methodStatuses.keySet().stream().findFirst()
                    .orElse(Paths.get("strata").toUri());
            return classifyRun(verifierExitCode, diagnostics,
                    preDiagnosticOutput.toString(), abortUri,
                    new VerificationResults(verifiedCount, failedCount,
                            failedAssertionsCount.getValue(), skippedCount));
        }
    }

    /**
     * Classify a finished backend run into its {@link JVerifyResults} variant.
     *
     * <p>Any non-zero exit is abnormal death: laurelAnalyzeBinary exits 0 on
     * every completed verification (pass or fail) -- exit 4 is synthesized
     * here from parsed diagnostics -- so a non-zero process exit means Strata
     * crashed (Lean panic / kill / OOM), never a normal "found failures" run.
     * This is a cross-package invariant of Strata's
     * {@code laurelAnalyzeBinaryCommand} (unlike laurelInterpretCommand, which
     * does exit with failuresFound after an interpreter error, and with
     * knownLimitation, mapped to 2 below, at a call its interpreter refuses to
     * run). A crash is classified
     * {@link JVerifyResults.DidNotRun} regardless of whether some diagnostics
     * were printed before it; otherwise a crash mid-print returns a partial
     * Ran verdict that an xfail test swallows as an "expected" AssertionError
     * (a false green). The abort diagnostic is only synthesized when nothing
     * was parsed.
     */
    static JVerifyResults classifyRun(int verifierExitCode,
                                      List<Diagnostic<?>> diagnostics,
                                      String preDiagnosticOutput,
                                      URI abortUri,
                                      VerificationResults verdict) {
        boolean aborted = verifierExitCode != 0;
        // Copy: a classifier should not mutate its argument.
        diagnostics = new ArrayList<>(diagnostics);
        if (aborted && diagnostics.isEmpty()) {
            var abortPos = new Position(1, 1);
            diagnostics.add(new StrataDiagnostic(abortUri,
                    new Range(abortPos, abortPos),
                    "verification aborted: Strata exited with code "
                    + verifierExitCode + " without reporting diagnostics: "
                    + preDiagnosticOutput.strip()));
        }
        var exitCode = switch (verifierExitCode) {
            case 0 -> diagnostics.isEmpty() ? 0 : 4;
            // Strata's 4 is a known limitation, not this tool's 4, "failures found": exit as
            // for a construct the frontend refuses.
            case 4 -> 2 /* picocli USAGE */;
            default -> verifierExitCode;
        };
        if (aborted) {
            return new JVerifyResults.DidNotRun(diagnostics, exitCode, verifierExitCode);
        }
        return new JVerifyResults.Ran(diagnostics, exitCode, verdict);
    }

    private static final Pattern DIAGNOSTIC_PATTERN =
            Pattern.compile("^(.+?):(\\d+)-(\\d+): (.+)$");

    /**
     * Parse one line of Strata's diagnostics section.
     *
     * <p>Everything Strata prints after the {@code ==== DIAGNOSTICS ====}
     * sentinel is a diagnostic, so no non-blank line may be dropped: a line
     * that does not match the {@code path:start-end: message} shape is
     * surfaced as a malformed-diagnostic error at the fallback location
     * rather than silently swallowed.
     *
     * @return the diagnostic, or {@code null} for a blank line.
     */
    static StrataDiagnostic parseDiagnosticLine(String line, PositionResolver positions, URI malformedFallbackUri) {
        Matcher matcher = DIAGNOSTIC_PATTERN.matcher(line);
        if (!matcher.matches()) {
            if (line.isBlank()) {
                return null;
            }
            var pos = new Position(1, 1);
            return new StrataDiagnostic(malformedFallbackUri, new Range(pos, pos),
                    "malformed diagnostic from Strata: " + line.strip());
        }
        String filePath = matcher.group(1);
        String message = matcher.group(4);

        if (filePath.contains("<unknown>")) {
            // A range the frontend deliberately stamped UNKNOWN (the input
            // carries no statement spans there): a real finding, just
            // without a source position -- not a malformed line.
            var pos = new Position(1, 1);
            return new StrataDiagnostic(malformedFallbackUri, new Range(pos, pos),
                    message + " (no source position: the input carries no statement spans there)");
        }

        try {
            // Offsets are the frontend's synthetic (line, column) packings,
            // which may exceed Integer.MAX_VALUE: parse as long, INSIDE the
            // try, so a genuinely malformed number degrades like any other
            // unresolvable location.
            long startOffset = Long.parseLong(matcher.group(2));
            long endOffset = Long.parseLong(matcher.group(3));

            // Strata emits `file://` URIs for program sources, and the
            // contract sources' `string:` and `jar:` URIs as given; parse
            // them as URIs rather than as filesystem paths, which would miss
            // the resolver and lose the source location.
            var uri = filePath.startsWith("file:") || filePath.startsWith("string:")
                    || filePath.startsWith("jar:")
                    ? URI.create(filePath)
                    : toDiagnosticUri(filePath);

            var range = new Range(
                    positions.positionOf(uri, startOffset),
                    positions.positionOf(uri, endOffset)
            );
            return new StrataDiagnostic(uri, range, message);
        } catch (RuntimeException e) {
            // A well-shaped line can still fail location resolution; degrade
            // to a counted malformed-diagnostic (same never-drop guarantee as
            // the regex-mismatch branch) instead of crashing the run.
            var pos = new Position(1, 1);
            return new StrataDiagnostic(malformedFallbackUri, new Range(pos, pos),
                    "malformed diagnostic from Strata (" + e.getMessage() + "): " + line.strip());
        }
    }

    /// Build a URI for a Strata diagnostic's (non-`file:`) source path.
    /// Normally a real filesystem path, but synthetic paths like "<unknown>"
    /// are not legal on every platform; fall back to a directly constructed
    /// file URI so the diagnostic still threads through.
    private static URI toDiagnosticUri(String filePath) {
        try {
            return Paths.get(filePath).toUri();
        } catch (InvalidPathException e) {
            return syntheticFileUri(filePath);
        }
    }

    static URI syntheticFileUri(String filePath) {
        String path = filePath.startsWith("/") ? filePath : "/" + filePath;
        try {
            return new URI("file", null, path, null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(
                    "Could not construct URI for diagnostic path: " + filePath, e);
        }
    }
}
