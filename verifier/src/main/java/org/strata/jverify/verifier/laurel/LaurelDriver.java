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
            return new JVerifyResults(diagnostics, CommandLine.ExitCode.USAGE, null);
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
                        Files.createDirectories(verifierOptions.printSerializedOutputProgram().getParent());
                        Files.writeString(verifierOptions.printSerializedOutputProgram(), files.toPrettyString());
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
                return new JVerifyResults(diagnostics, CommandLine.ExitCode.OK, null);
            }

            var results = runVerifier(analysisResult.filesMap(), serializedProgram);
            var allDiagnostics = new ArrayList<>(diagnostics);
            allDiagnostics.addAll(results.diagnostics());
            return new JVerifyResults(allDiagnostics, results.exitCode(), results.verificationResults());
        }
    }

    public JVerifyResults runVerifier(FilesMap filesMap, IonValue serializedProgram) {
        var processBuilder = new ProcessBuilder(
                "lake", "exe", "-q", "strata", "laurelAnalyzeBinary", "--solver", "z3"
        );
        // The `strata` executable lives in the StrataCLI subpackage, so `lake` must be invoked from there.
        processBuilder.directory(verifierOptions.backendPath().resolve("StrataCLI").toFile());
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
                verifierOptions.outWriter().println("Failed to use Strata at: " + verifierOptions.backendPath() +
                        "\nError message: " + e.getMessage());
                return new JVerifyResults(new ArrayList<>(), -1, null);
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

            String line;
            boolean inDiagnosticsSection = false;
            StringBuilder preDiagnosticOutput = new StringBuilder();
            Pattern diagnosticPattern = Pattern.compile("^(.+?):(\\d+)-(\\d+): (.+)$");

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
                    Matcher matcher = diagnosticPattern.matcher(line);
                    if (matcher.matches()) {
                        String filePath = matcher.group(1);
                        int startOffset = Integer.parseInt(matcher.group(2));
                        int endOffset = Integer.parseInt(matcher.group(3));
                        String message = matcher.group(4);

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

                        var diagnostic = new StrataDiagnostic(uri, range, message);
                        diagnostics.add(diagnostic);

                        failedAssertionsCount.setValue(failedAssertionsCount.getValue() + 1);

                        var uriMethods = methodStatuses.get(uri);
                        if (uriMethods != null) {
                            var failedJavaMethod = uriMethods.findAtPoint(diagnostic.range.start().line());
                            if (failedJavaMethod != null) {
                                failedJavaMethod.setVerificationStatus(JavaMethodVerificationStatus.VerificationStatus.Failed);
                            }
                        }
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

            int verifierExitCode = process.waitFor();
            if (verifierExitCode != 0 && diagnostics.isEmpty()) {
                options.outWriter().println("Strata exited with code " + verifierExitCode + ":\n" + preDiagnosticOutput);
                // Soundness: a non-zero Strata exit with no parsed
                // diagnostics means verification aborted (e.g. an internal
                // Strata exception such as a failed termination check).
                // Surface it as an error so the result can never be read as
                // a vacuous "0 errors / verified" -- the same invariant the
                // Laurel-to-Core soundness net enforces on the Strata side.
                var abortUri = methodStatuses.keySet().stream().findFirst()
                        .orElse(Paths.get("strata").toUri());
                var abortPos = new org.strata.jverify.common.Position(1, 1);
                diagnostics.add(new StrataDiagnostic(abortUri,
                        new Range(abortPos, abortPos),
                        "verification aborted: Strata exited with code "
                        + verifierExitCode + " without reporting diagnostics: "
                        + preDiagnosticOutput.toString().strip()));
                failedAssertionsCount.setValue(failedAssertionsCount.getValue() + 1);
                // The run aborted before producing per-method results, so
                // the optimistic "Verified" defaults are not trustworthy:
                // do not report any method as verified or skipped.
                verifiedCount = 0;
                skippedCount = 0;
            }
            var exitCode = verifierExitCode == 0 ? (diagnostics.isEmpty() ? 0 : 4) : verifierExitCode;
            return new JVerifyResults(diagnostics, exitCode,
                    new VerificationResults(verifiedCount, failedCount, 
                            failedAssertionsCount.getValue(), skippedCount));
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

