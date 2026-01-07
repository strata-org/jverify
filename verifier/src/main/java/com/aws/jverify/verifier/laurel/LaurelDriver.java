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
import java.nio.file.Path;
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

    public JVerifyResults verifyJavaFile(JavaFileObject javaFile)
            throws IOException {
        return verifyJavaFiles(List.of(javaFile));
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

    public JVerifyResults runVerifier(FilesMap diagnosticHelper, NameCompiler nameCompiler, IonValue serializedProgram) {
        checkVerifierVersion();

//        Options:
//        --verbose                   Print extra information during analysis.
//        --check                     Process up until SMT generation, but don't solve.
//        --type-check                Exit after semantic dialect's type inference/checking.
//        --parse-only                Exit after DDM parsing and type checking.
//        --stop-on-first-error       Exit after the first verification error.
//        --solver-timeout <seconds>  Set the solver time limit per proof goal.
        var processBuilder = new ProcessBuilder(
                "lake", "exe", "strata", "laurelAnalyze"
        );
        processBuilder.directory(verifierOptions.backendPath().toFile());
//        if (verifierOptions.printDeserializedTarget() != null) {
//            processBuilder.command().add("--print=" + verifierOptions.printDeserializedTarget());
//        }
//        applyPositionFilter(verifierOptions, processBuilder);
//        if (verifierOptions.showRanges()) {
//            // --show-snippets has no affect because Dafny can't extract them from the serialized source anyways
//            processBuilder.command().add("--show-snippets=false");
//            processBuilder.command().add("--print-ranges");
//        }
//        processBuilder.command().add("--ignore-indentation");
//        for (var option : verifierOptions.additionalDafnyArguments()) {
//            processBuilder.command().add(option);
//        }

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
                return parseStrataOutput(diagnosticHelper, verifierOptions, nameCompiler, process);
            } catch (InterruptedException | IOException e) {
                verifierOptions.outWriter().println("Failed to use Dafny at: " + verifierOptions.backendPath());
                e.printStackTrace();
                return new JVerifyResults(List.of(), -1, null);
            }
        });
    }

    private static void applyPositionFilter(VerifierOptions verifierOptions, ProcessBuilder processBuilder) {
        PositionFilter positionFilter = verifierOptions.positionFilter();
        if (positionFilter == null || positionFilter.includeDependencies()) {
            return;
        }

        var s = new StringBuilder();
        s.append("--filter-position=");
        if (positionFilter.fileEnding() != null) {
            s.append(positionFilter.fileEnding());
        }
        boolean hasLineFilter = positionFilter.start() != null || positionFilter.end() != null;
        if (hasLineFilter) {
            s.append(":");
        }
        s.append(positionFilter.start() == null ? "" : positionFilter.start());
        if (hasLineFilter) {
            s.append("-");
        }
        s.append(positionFilter.end() == null ? "" : positionFilter.end());
        processBuilder.command().add(s.toString());
    }

    public static int getExitCodeFromDafny(int dafnyExitCode) {
        return dafnyExitCode == 2 ? Integer.parseInt("2" + dafnyExitCode) : dafnyExitCode;
    }

    private static final Pattern dafnySummaryPattern = Pattern.compile(
            "Dafny program verifier finished with (?<VerifiedCount>\\d+) (assertions )?verified, (?<ErrorCount>\\d+) errors?");

    private static final Pattern timePattern = Pattern.compile("Time to do (?<Name>\\w+) was (?<Duration>\\d+)ms");

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

            // Read and parse Strata output
            String line;
            boolean inDiagnosticsSection = false;
            Pattern diagnosticPattern = Pattern.compile("^(.+?):(\\d+)-(\\d+): (.+)$");

            while ((line = strataOutput.readLine()) != null) {
                if (options.verbose()) {
                    options.outWriter().println(line);
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

                        // Create URI from file path
                        var uri = Paths.get(filePath).toUri();

                        var range = new Range(
                                filesMap.computePositionFromFileOffset(uri, startOffset), 
                                filesMap.computePositionFromFileOffset(uri, endOffset)
                        );

                        var diagnostic = new StrataDiagnostic(uri, range, message);
                        diagnostics.add(diagnostic);

                        // Increment failed assertions count
                        failedAssertionsCount.setValue(failedAssertionsCount.getValue() + 1);

                        // Update method status
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
            var exitCode = getExitCodeFromDafny(verifierExitCode);
            return new JVerifyResults(diagnostics, exitCode,
                    new VerificationResults(verifiedCount, failedCount, 
                            failedAssertionsCount.getValue(), skippedCount, performanceTicks.getValue()));
        }

    }

    /**
     * Used as an {@link ObjectMapper} mixin when parsing {@link Position},
     * since Dafny JSON diagnostics include a {@code pos} field that we don't use or need.
     */
    @JsonIgnoreProperties({"pos"})
    private static abstract class DafnyJsonPosition {
    }

    /**
     * Simple implementation of Diagnostic for Strata verification errors.
     */
    private static class StrataDiagnostic implements Diagnostic<JavaFileObject> {
        private final URI uri;
        private final Range range;
        private final String message;

        public DafnyDiagnostic.Location location;
        public StrataDiagnostic(URI uri, Range range, String message) {
            this.uri = uri;
            this.range = range;
            this.message = message;
        }

        @Override
        public Kind getKind() {
            return Kind.ERROR;
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
            return NOPOS;
        }

        @Override
        public String getCode() {
            return "strata.verification.error";
        }

        @Override
        public String getMessage(Locale locale) {
            return message + " (at " + uri + ":" + range + ")";
        }
    }

}

