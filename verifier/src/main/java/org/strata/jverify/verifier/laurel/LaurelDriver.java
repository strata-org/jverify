package org.strata.jverify.verifier.laurel;

import com.amazon.ion.*;
import com.amazon.ion.system.IonBinaryWriterBuilder;
import com.amazon.ion.system.IonSystemBuilder;
import org.strata.jverify.common.Range;
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
import java.nio.file.Files;
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

                // Combine all file programs into a single Program
                var allProcedures = new ArrayList<org.strata.jverify.laurel.Procedure>();
                var allFields = new ArrayList<org.strata.jverify.laurel.Field>();
                var allTypes = new ArrayList<org.strata.jverify.laurel.TypeDefinition>();
                var allConstants = new ArrayList<org.strata.jverify.laurel.Constant>();
                for (LaurelFile file : analysisResult.files()) {
                    var prog = file.program();
                    allProcedures.addAll(prog.staticProcedures());
                    allFields.addAll(prog.staticFields());
                    allTypes.addAll(prog.types());
                    allConstants.addAll(prog.constants());
                }
                var combined = new org.strata.jverify.laurel.Program(
                        allProcedures, allFields, allTypes, allConstants);
                var programIon = combined.toIon(ion);

                if (verifierOptions.printSerializedOutputProgram() != null) {
                    try {
                        Files.createDirectories(verifierOptions.printSerializedOutputProgram().getParent());
                        Files.writeString(verifierOptions.printSerializedOutputProgram(), programIon.toPrettyString());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                return programIon;
            });

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
                var msg = "Failed to use Strata at: " + verifierOptions.backendPath() +
                        "\nError message: " + e.getMessage();
                verifierOptions.outWriter().println(msg);
                System.err.println(msg);
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

                        var uri = Paths.get(filePath).toUri();

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
                var msg = "Strata exited with code " + verifierExitCode + ":\n" + preDiagnosticOutput;
                options.outWriter().println(msg);
                System.err.println(msg);
            }
            var exitCode = verifierExitCode == 0 ? (diagnostics.isEmpty() ? 0 : 4) : verifierExitCode;
            return new JVerifyResults(diagnostics, exitCode,
                    new VerificationResults(verifiedCount, failedCount, 
                            failedAssertionsCount.getValue(), skippedCount));
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
            return Paths.get(uri.getPath()).getFileName().toString();
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

