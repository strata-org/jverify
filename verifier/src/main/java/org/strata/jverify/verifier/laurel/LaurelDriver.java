package org.strata.jverify.verifier.laurel;

import com.amazon.ion.*;
import com.amazon.ion.system.IonBinaryWriterBuilder;
import com.amazon.ion.system.IonSystemBuilder;
import org.strata.jverify.common.Range;
import org.strata.jverify.laurel.IonSerializer;
import org.strata.jverify.verifier.*;
import org.strata.jverify.verifier.compiler.Reporter;
import org.strata.jverify.verifier.compiler.generator.laurel.JavaToLaurelCompiler;
import org.strata.jverify.verifier.compiler.generator.laurel.LaurelFile;
import org.strata.jverify.verifier.compiler.simplifications.VerifyAnnotationCompiler;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JavacMessages;
import picocli.CommandLine;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LaurelDriver implements Driver {

    private final Context context;
    private final VerifierOptions verifierOptions;

    @Override
    public VerifierOptions getVerifierOptions() {
        return verifierOptions;
    }

    public LaurelDriver(Context context) {
        this.context = context;
        this.verifierOptions = context.get(VerifierOptions.class);
    }

    public JVerifyResults verifyJavaFiles(
            List<JavaFileObject> readFiles
    ) {
        var diagnostics = new ArrayList<Diagnostic<?>>();

        var messages = JavacMessages.instance(context);
        messages.add("org.strata.jverify.messages");

        var analysisResult = verifierOptions.time("Compiling Java to Laurel",
                () -> new JavaToLaurelCompiler(context).analyzeJavaCode(verifierOptions, readFiles));

        var hasErrors = false;
        for (var diagnostic : Reporter.instance(context).diagnostics.getDiagnostics()) {
            diagnostics.add(diagnostic);
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                hasErrors = true;
            }
        }
        if (analysisResult == null || (hasErrors && !verifierOptions.continueOnErrors())) {
            return new JVerifyResults.NoVerdict(diagnostics, CommandLine.ExitCode.USAGE);
        } else {
            var serializedProgram = verifierOptions.time("Serializing Laurel AST", () -> {

                var ion = IonSystemBuilder.standard().build();
                IonList files = ion.newEmptyList();

                for (LaurelFile file : analysisResult.files()) {
                    IonStruct strataFile = ion.newEmptyStruct();

                    String filePath = Paths.get("").toUri().relativize(file.uri()).toString();
                    strataFile.put("filePath", ion.newString(filePath));

                    // Create the program Ion structure
                    IonList programAsIon = ion.newEmptyList();
                    IonSexp header = ion.newEmptySexp();
                    header.add(ion.newSymbol("program"));
                    header.add(ion.newString("Laurel"));
                    programAsIon.add(header);
                    var serializer = new IonSerializer(ion);
                    for (var command : file.commands()) {
                        programAsIon.add(serializer.serializeCommand(command));
                    }
                    strataFile.put("program", programAsIon);

                    files.add(strataFile);
                }

                if (verifierOptions.printSerializedOutputProgram() != null) {
                    try {
                        var outPath = verifierOptions.printSerializedOutputProgram();
                        if (outPath.getParent() != null) {
                            Files.createDirectories(outPath.getParent());
                        }
                        // Emit *binary* Ion so the serialized Laurel program can be
                        // consumed directly by Strata's binary Ion reader (e.g. the
                        // `laurelIonToHorn` CHC CLI), matching the binary stream used
                        // on the in-process verification path below.
                        try (var out = Files.newOutputStream(outPath);
                             var writer = IonBinaryWriterBuilder.standard().build(out)) {
                            files.writeTo(writer);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                return files;
            });

            // --emit-laurel separates compilation (Java -> Laurel IR) from
            // verification (Laurel IR -> SMT): once the serialized Laurel
            // program has been written, stop here without invoking the
            // backend. Verification can then be run separately on the
            // emitted Ion.
            if (verifierOptions.emitLaurelOnly()) {
                return new JVerifyResults.NoVerdict(diagnostics, CommandLine.ExitCode.OK);
            }

            var results = runVerifier(analysisResult.filesMap(), serializedProgram);
            var allDiagnostics = new ArrayList<Diagnostic<?>>(diagnostics);
            allDiagnostics.addAll(results.diagnostics());
            // Preserve the variant while prepending the compile-phase diagnostics.
            return switch (results) {
                case JVerifyResults.Ran r ->
                        new JVerifyResults.Ran(allDiagnostics, r.exitCode(), r.verificationResults());
                case JVerifyResults.DidNotRun d ->
                        new JVerifyResults.DidNotRun(allDiagnostics, d.exitCode());
                case JVerifyResults.NoVerdict n ->
                        new JVerifyResults.NoVerdict(allDiagnostics, n.exitCode());
            };
        }
    }

    public JVerifyResults runVerifier(FilesMap filesMap, IonValue serializedProgram) {
        final List<String> baseCommand;
        if (verifierOptions.backendPath() != null) {
            // Explicit --strata path: invoke via `lake exe` from the StrataCLI subdir.
            baseCommand = List.of("lake", "exe", "-q", "strata");
        } else {
            // Rely on `strata` being on PATH. Under Brazil this is achieved
            // by declaring StrataCLI as a build-tool: its `runtime/bin/` is
            // prepended to PATH by `brazil-build`, and that PATH is inherited
            // by Gradle and by each Test task's forked JVM.
            baseCommand = List.of("strata");
        }
        var command = new ArrayList<String>(baseCommand);
        command.add("laurelAnalyzeBinary");
        command.add("--solver");
        command.add("z3");
        var processBuilder = new ProcessBuilder(command);
        if (verifierOptions.backendPath() != null) {
            // The `strata` executable lives in the StrataCLI subpackage, so `lake` must be invoked from there.
            processBuilder.directory(verifierOptions.backendPath().resolve("StrataCLI").toFile());
        }
        return verifierOptions.time("Running Strata", () -> {
            try (var process = new AutoClosingProcessWrapper(processBuilder.redirectErrorStream(true).start()))
            {
                try (var strataStdin = process.getProcess().getOutputStream();
                     var writer = IonBinaryWriterBuilder.standard().build(strataStdin))
                {
                    serializedProgram.writeTo(writer);
                }
                return parseStrataOutput(filesMap, verifierOptions, process.getProcess());
            } catch (InterruptedException | IOException e) {
                String location = verifierOptions.backendPath() != null
                        ? verifierOptions.backendPath().toString()
                        : "strata on PATH (pass --strata <path> or set JVERIFY_STRATA)";
                verifierOptions.outWriter().println("Failed to use Strata at: " + location +
                        "\nError message: " + e.getMessage());
                // Backend failed to launch: not a verification outcome.
                return new JVerifyResults.DidNotRun(new ArrayList<>(), -1);
            }
        });
    }
    
    class AutoClosingProcessWrapper implements AutoCloseable {
        private final Process process;

        public AutoClosingProcessWrapper(Process process) {
            this.process = process;
        }

        public Process getProcess() {
            return process;
        }

        @Override
        public void close() {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
    
    private JVerifyResults parseStrataOutput(FilesMap filesMap,
                                             VerifierOptions options,
                                             Process process) throws IOException, InterruptedException {

        var diagnostics = new ArrayList<Diagnostic<?>>();
        try (var strataOutput = process.inputReader()) {
            Wrapper<Integer> failedAssertionsCount = new Wrapper<>(0);
            var annotationCompiler = context.get(VerifyAnnotationCompiler.class);
            var methodStatuses = annotationCompiler.getMethodStatusPerUri();

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
                    // malformed diagnostic carries no real location, so it must
                    // not be attributed to any method (its position (1,1)
                    // would otherwise spuriously fail whatever method spans
                    // line 1 of a real file, and suppress the unattributed-
                    // errors note below).
                    var fallbackUri = Paths.get("strata-unattributed").toUri();
                    var diagnostic = parseDiagnosticLine(line, filesMap, fallbackUri);
                    if (diagnostic == null) {
                        continue;
                    }
                    diagnostics.add(diagnostic);
                    failedAssertionsCount.setValue(failedAssertionsCount.getValue() + 1);

                    var uriMethods = methodStatuses.get(diagnostic.getUri());
                    var failedJavaMethod = uriMethods == null
                            ? null
                            : uriMethods.findAtPoint(diagnostic.range.start().line());
                    if (failedJavaMethod != null) {
                        failedJavaMethod.setVerificationStatus(JavaMethodVerificationStatus.VerificationStatus.Failed);
                    } else {
                        unattributedErrorCount++;
                    }
                }
            }

            for (IntervalTree<Integer, JavaMethodVerificationStatus> uriStatuses : methodStatuses.values()) {
                var statuses = uriStatuses.streamNodes().toList();
                for (var methodStatus : statuses) {
                    var method = methodStatus.getValue();
                    var status = method.getVerificationStatus();
                    switch (status) {
                        case Verified -> verifiedCount++;
                        case Skipped -> skippedCount++;
                        case Failed -> failedCount++;
                    }
                }
            }

            // Honesty in the summary: an error that could not be attributed
            // to any method (no range, or a range outside every method) leaves
            // the per-method counts untouched. Say so whenever at least one
            // such error exists — including mixed runs where other errors were
            // attributed — rather than reporting method counts that silently
            // omit some errors. Counted per diagnostic at attribution time
            // (not failedAssertions - failedMethods, which over-reports when
            // two errors land in the same method).
            if (unattributedErrorCount > 0) {
                options.outWriter().println("Note: " + unattributedErrorCount
                        + " error(s) could not be attributed to a specific method;"
                        + " the method counts below do not reflect them.");
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
     * every completed verification (pass or fail) -- exit 4 is synthesized here
     * from parsed diagnostics -- so a non-zero process exit means Strata
     * crashed (Lean panic / kill / OOM), never a normal "found failures" run.
     * This is a cross-package invariant of StrataCLI's {@code
     * laurelAnalyzeBinaryCommand} (in StrataMainLib.lean): that callback prints
     * the diagnostics block and returns without an {@code IO.Process.exit}, so
     * "found failures" never becomes a non-zero exit on this path (unlike
     * laurelInterpretCommand, which does exit with failuresFound). If that
     * callback is ever changed to exit non-zero on failures, every xfail test
     * that reaches a verdict would start throwing here -- so it must not.
     * It is classified {@link JVerifyResults.DidNotRun} regardless of whether
     * some diagnostics were printed before the crash; otherwise a crash
     * mid-print returns a partial Ran verdict that an xfail test swallows as an
     * "expected" AssertionError (a false green). The abort diagnostic is only
     * synthesized when nothing was parsed (keeps the #447 backstop reporting
     * for the empty case); a crash after partial output is already surfaced
     * loud via the DidNotRun return.
     *
     * <p>Extracted (and package-visible) so this classification -- the
     * soundness core of the sealed-results tripwire -- is pinned by unit tests
     * (LaurelDriverClassifyTest); a refactor that regresses it must fail there.
     */
    static JVerifyResults classifyRun(int verifierExitCode,
                                      java.util.List<Diagnostic<?>> diagnostics,
                                      String preDiagnosticOutput,
                                      java.net.URI abortUri,
                                      VerificationResults verdict) {
        boolean aborted = verifierExitCode != 0;
        // Copy: a classifier should not mutate its argument.
        diagnostics = new java.util.ArrayList<>(diagnostics);
        if (aborted && diagnostics.isEmpty()) {
            var abortPos = new org.strata.jverify.common.Position(1, 1);
            diagnostics.add(new StrataDiagnostic(abortUri,
                    new Range(abortPos, abortPos),
                    "verification aborted: Strata exited with code "
                    + verifierExitCode + " without reporting diagnostics: "
                    + preDiagnosticOutput.strip()));
        }
        var exitCode = verifierExitCode == 0 ? (diagnostics.isEmpty() ? 0 : 4) : verifierExitCode;
        if (aborted) {
            return new JVerifyResults.DidNotRun(diagnostics, exitCode);
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
     * that does not match the expected {@code path:start-end: message} shape
     * (for example an empty file path when a source range was lost upstream)
     * is surfaced as a malformed-diagnostic error at the fallback location
     * rather than silently swallowed — dropping it would turn a failing
     * verification into a vacuous "Found 0 errors".
     *
     * @return the diagnostic, or {@code null} for a blank line.
     */
    static StrataDiagnostic parseDiagnosticLine(String line, FilesMap filesMap, URI malformedFallbackUri) {
        Matcher matcher = DIAGNOSTIC_PATTERN.matcher(line);
        if (!matcher.matches()) {
            if (line.isBlank()) {
                return null;
            }
            var pos = new org.strata.jverify.common.Position(1, 1);
            return new StrataDiagnostic(malformedFallbackUri, new Range(pos, pos),
                    "malformed diagnostic from Strata: " + line.strip());
        }
        String filePath = matcher.group(1);
        int startOffset = Integer.parseInt(matcher.group(2));
        int endOffset = Integer.parseInt(matcher.group(3));
        String message = matcher.group(4);

        try {
            // Strata emits `file://` URIs (e.g. file:///abs/Path.java);
            // parse them as URIs rather than treating the URI string as
            // a filesystem path, which would miss the filesMap and lose
            // the source location (reported as 1:1).
            var uri = filePath.startsWith("file:")
                    ? URI.create(filePath)
                    : toDiagnosticUri(filePath);

            var range = new Range(
                    filesMap.computePositionFromFileOffset(uri, startOffset),
                    filesMap.computePositionFromFileOffset(uri, endOffset)
            );
            return new StrataDiagnostic(uri, range, message);
        } catch (RuntimeException e) {
            // A well-shaped line can still fail location resolution: the URI
            // may parse but match no line map (computePositionFromFileOffset
            // throws), or not parse at all (URI.create on a file: path with a
            // space). Degrade to a counted malformed-diagnostic — the same
            // never-drop guarantee as the regex-mismatch branch — instead of
            // letting the exception escape runVerifier and crash the run.
            var pos = new org.strata.jverify.common.Position(1, 1);
            return new StrataDiagnostic(malformedFallbackUri, new Range(pos, pos),
                    "malformed diagnostic from Strata (" + e.getMessage() + "): " + line.strip());
        }
    }

    /// Build a URI for a Strata diagnostic's (non-`file:`) source path.
    /// Normally this is a real filesystem path, but Strata also reports
    /// diagnostics for simplification-injected trees against a synthetic
    /// "<unknown>" path, which is not a legal filesystem path on every
    /// platform ('<' and '>' are illegal on Windows). Fall back to a
    /// directly constructed file URI in that case so the diagnostic still
    /// threads through to the JavaToLaurelCompiler line-map fallback
    /// instead of crashing with InvalidPathException.
    private static URI toDiagnosticUri(String filePath) {
        try {
            return Paths.get(filePath).toUri();
        } catch (InvalidPathException e) {
            return syntheticFileUri(filePath);
        }
    }

    /// Construct a `file:` URI from a path that is not a legal filesystem
    /// path on this platform. The multi-argument URI constructor
    /// percent-encodes illegal characters; getPath() decodes them back, so
    /// the line-map fallback's path-suffix match still triggers.
    static URI syntheticFileUri(String filePath) {
        String path = filePath.startsWith("/") ? filePath : "/" + filePath;
        try {
            return new URI("file", null, path, null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(
                    "Could not construct URI for diagnostic path: " + filePath, e);
        }
    }

    public static class StrataDiagnostic implements Diagnostic<JavaFileObject>, DiagnosticWithRange {
        private static final int SEVERITY_ERROR = 1;
        private static final int SEVERITY_WARNING = 2;
        private static final int SEVERITY_INFO = 4;

        private final URI uri;
        private final Range range;
        private final String message;
        private final int severity;

        public StrataDiagnostic(URI uri, Range range, String message) {
            this(uri, range, message, SEVERITY_ERROR);
        }

        public StrataDiagnostic(URI uri, Range range, String message, int severity) {
            this.uri = uri;
            this.range = range;
            this.message = message;
            this.severity = severity;
        }

        public URI getUri() {
            return uri;
        }

        public Range getRange() {
            return range;
        }

        @Override
        public String filePath() {
            var uri = getUri();
            return uri.getPath();
        }

        @Override
        public String filename() {
            // Avoid Paths.get here: the diagnostic URI may be the synthetic
            // "<unknown>" path, which is not a legal filesystem path on
            // every platform. URI paths always use '/', so the file name is
            // the segment after the last '/'.
            String path = uri.getPath();
            int slash = path.lastIndexOf('/');
            return slash >= 0 ? path.substring(slash + 1) : path;
        }

        @Override
        public Kind getKind() {
            return switch (severity) {
                case SEVERITY_ERROR -> Kind.ERROR;
                case SEVERITY_WARNING -> Kind.WARNING;
                case SEVERITY_INFO -> Kind.NOTE;
                default -> Kind.ERROR;
            };
        }

        @Override
        public JavaFileObject getSource() {
            return null;
        }

        @Override
        public long getPosition() {
            return NOPOS;
        }

        @Override
        public long getStartPosition() {
            return NOPOS;
        }

        @Override
        public long getEndPosition() {
            return NOPOS;
        }

        @Override
        public long getLineNumber() {
            return range.start().line();
        }

        @Override
        public long getColumnNumber() {
            return range.start().character();
        }

        @Override
        public String getCode() {
            return "strata.verification.error";
        }

        @Override
        public String getMessage(Locale locale) {
            return getSeverityMessage() + ": " + message;
        }

        private String getSeverityMessage() {
            return switch(severity) {
                case SEVERITY_WARNING -> "Warning";
                case SEVERITY_ERROR -> "Error";
                case SEVERITY_INFO -> "Info";
                default -> "Error";
            };
        }

    }

}

