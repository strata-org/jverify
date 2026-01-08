package com.aws.jverify.verifier.laurel;

import com.amazon.ion.*;
import com.amazon.ion.system.IonBinaryWriterBuilder;
import com.amazon.ion.system.IonSystemBuilder;
import com.aws.jverify.common.Position;
import com.aws.jverify.common.Range;
import com.aws.jverify.laurel.IonSerializer;
import com.aws.jverify.verifier.*;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.generator.laurel.JavaToLaurelCompiler;
import com.aws.jverify.verifier.compiler.generator.laurel.LaurelFile;
import com.aws.jverify.verifier.compiler.simplifications.NameCompiler;
import com.aws.jverify.verifier.compiler.simplifications.VerifyAnnotationCompiler;
import com.aws.jverify.verifier.dafny.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public final Context context;
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
        List<Diagnostic<?>> diagnostics = new ArrayList<>();

        var messages = JavacMessages.instance(context);
        messages.add("com.aws.jverify.messages");

        var analysisResult = verifierOptions.time("Compiling Java to Dafny",
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
                    programAsIon.add(new IonSerializer(ion).serializeCommand(file.root()));
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

            var results = runVerifier(analysisResult.filesMap(), NameCompiler.instance(context), serializedProgram);
            results.diagnostics().addAll(0, diagnostics);
            return results;
        }
    }

    private static boolean checkedVersion = false;

    private void checkVerifierVersion() {
        if (!verifierOptions.testBackendVersion()) {
            return;
        }

        if (!checkedVersion) {
            checkedVersion = true;
        }
    }

    public JVerifyResults runVerifier(FilesMap filesMap, NameCompiler nameCompiler, IonValue serializedProgram) {
        checkVerifierVersion();

        var processBuilder = new ProcessBuilder(
                "lake", "exe",  "-q", "strata", "laurelAnalyze"
        );
        processBuilder.directory(verifierOptions.backendPath().toFile());

        if (verifierOptions.verbose()) {
            verifierOptions.outWriter().println("Verifier options: " + String.join(" ", processBuilder.command()));
        }

        return verifierOptions.time("Running Strata", () -> {
            try {
                // Redirect stderr into stdout, instead of reading one and then the other,
                // in order to preserve the order of output and to avoid potential deadlock.
                var process = processBuilder.redirectErrorStream(true).start();
                try (var strataStdin = process.getOutputStream();
                     var writer = IonBinaryWriterBuilder.standard().build(strataStdin)) {
                    serializedProgram.writeTo(writer);
                }
                return parseStrataOutput(filesMap, verifierOptions, nameCompiler, process);
            } catch (InterruptedException | IOException e) {
                verifierOptions.outWriter().println("Failed to use Dafny at: " + verifierOptions.backendPath());
                e.printStackTrace();
                return new JVerifyResults(List.of(), -1, null);
            }
        });
    }

    /**
     * Parses the given {@code dafny verify} output,
     * adding both diagnostics and the summary verified/error counts to {@code outResults}.
     * Note that Dafny must be invoked with {@code --json-diagnostics} or else parsing will fail.
     */
    private JVerifyResults parseStrataOutput(FilesMap filesMap,
                                             VerifierOptions options,
                                             NameCompiler nameCompiler,
                                             Process process) throws IOException, InterruptedException {

        List<Diagnostic<?>> diagnostics = new ArrayList<>();
        try (var strataOutput = process.inputReader()) {

            Wrapper<Integer> performanceTicks = new Wrapper<>(null);
            Wrapper<Integer> failedAssertionsCount = new Wrapper<>(0);
            var annotationCompiler = context.get(VerifyAnnotationCompiler.class);
            var methodStatusses = annotationCompiler.getMethodStatusPerUri();

            int verifiedCount = 0;
            int skippedCount = 0;
            int failedCount = 0;

            String line;
            boolean inDiagnosticsSection = false;
            Pattern diagnosticPattern = Pattern.compile("^(.+?):(\\d+)-(\\d+): (.+)$");

            while ((line = strataOutput.readLine()) != null) {
                if (options.verbose()) {
                    options.outWriter().println(line);
                }
                if (line.contains("Exception")) {
                    throw new RuntimeException(line);
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

                        var uriMethods = methodStatusses.get(uri);
                        if (uriMethods != null) {
                            var failedJavaMethod = uriMethods.findAtPoint(diagnostic.range.start().line());
                            if (failedJavaMethod != null) {
                                failedJavaMethod.setVerificationStatus(JavaMethodVerificationStatus.VerificationStatus.Failed);
                            }
                        }
                    }
                }
            }

            for (IntervalTree<Integer, JavaMethodVerificationStatus> uriStatusses : methodStatusses.values()) {
                var statusses = uriStatusses.streamNodes().toList();
                for (var methodStatus : statusses) {
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
            var exitCode = verifierExitCode == 0 ? (diagnostics.isEmpty() ? 0 : 4) : verifierExitCode;
            return new JVerifyResults(diagnostics, exitCode,
                    new VerificationResults(verifiedCount, failedCount, 
                            failedAssertionsCount.getValue(), skippedCount, performanceTicks.getValue()));
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

        public DafnyDiagnostic.Location location;

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

