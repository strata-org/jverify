package com.aws.jverify.verifier.laurel;

import com.amazon.ion.*;
import com.amazon.ion.system.IonBinaryWriterBuilder;
import com.amazon.ion.system.IonSystemBuilder;
import com.amazon.ion.system.IonTextWriterBuilder;
import com.aws.jverify.common.Position;
import com.aws.jverify.laurel.IonSerializer;
import com.aws.jverify.verifier.*;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.frontend.InstrumentLower;
import com.aws.jverify.verifier.compiler.generator.dafny.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.frontend.TypesWithoutErasure;
import com.aws.jverify.verifier.compiler.generator.laurel.JavaToLaurelCompiler;
import com.aws.jverify.verifier.compiler.generator.laurel.LaurelFile;
import com.aws.jverify.verifier.compiler.simplifications.NameCompiler;
import com.aws.jverify.verifier.compiler.simplifications.VerifyAnnotationCompiler;
import com.aws.jverify.verifier.dafny.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JavacMessages;
import picocli.CommandLine;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    /**
     * Compute line offsets for a source string.
     * Returns an array of byte positions where each line starts.
     * The first entry is always 0.
     */
    private static List<Integer> computeLineOffsets(String source) {
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0); // First line always starts at 0

        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                // Add position after the newline
                offsets.add(i + 1);
            }
        }

        return offsets;
    }

    public JVerifyResults verifyJavaFile(JavaFileObject javaFile)
            throws IOException {
        return verifyJavaFiles(List.of(javaFile));
    }

    public JVerifyResults verifyJavaFiles(
            List<JavaFileObject> readFiles
    ) throws IOException {
        List<Diagnostic<?>> diagnostics = new ArrayList<>();

        var messages = JavacMessages.instance(context);
        messages.add("com.aws.jverify.messages");

        var compiledProgram = verifierOptions.time("Compiling Java to Dafny",
                () -> new JavaToLaurelCompiler(context).analyzeJavaCode(verifierOptions, readFiles));

        var hasErrors = false;
        for (var diagnostic : Reporter.instance(context).diagnostics.getDiagnostics()) {
            diagnostics.add(diagnostic);
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                hasErrors = true;
            }
        }
        if (compiledProgram == null || (hasErrors && !verifierOptions.continueOnErrors())) {
            return new JVerifyResults(diagnostics, CommandLine.ExitCode.USAGE, null);
        } else {
            var serializedProgram = verifierOptions.time("Serializing Laurel AST", () -> {

                var ion = IonSystemBuilder.standard().build();


                // Create list of StrataFile structs
                IonList files = ion.newEmptyList();

                // Create a StrataFile for each input file
                for (LaurelFile file : compiledProgram) {
                    IonStruct strataFile = ion.newEmptyStruct();

                    // Add filePath
                    String filePath = file.uri().toString();
                    strataFile.put("filePath", ion.newString(filePath));

                    // Create the program Ion structure
                    IonList programAsIon = ion.newEmptyList();
                    IonSexp header = ion.newEmptySexp();
                    header.add(ion.newSymbol("program"));
                    header.add(ion.newString("Laurel"));
                    programAsIon.add(header);
                    programAsIon.add(new IonSerializer(ion).serializeCommand(file.root()));
                    
                    // Add program (all files share the same combined program for now)
                    strataFile.put("program", programAsIon.clone());

                    IonList lineOffsets = ion.newEmptyList();
                    for (Integer offset : file.lineOffsets()) {
                        lineOffsets.add(ion.newInt(offset));
                    }
                    strataFile.put("lineOffsets", lineOffsets);

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
            var results = runVerifier(NameCompiler.instance(context), serializedProgram);
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

    public JVerifyResults runVerifier(NameCompiler nameCompiler, IonValue serializedProgram) {
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
                return parseStrataOutput(verifierOptions, nameCompiler, process);
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
    private JVerifyResults parseStrataOutput(VerifierOptions options,
                                                    NameCompiler nameCompiler,
                                                    Process process) throws IOException, InterruptedException {

        List<Diagnostic<?>> diagnostics = new ArrayList<>();
        try (var dafnyOutput = process.inputReader()) {

            var objectMapper = new ObjectMapper();

            SimpleModule module = new SimpleModule();
            module.addDeserializer(DafnyOutput.class, new DafnyOutputDeserializer(objectMapper));
            objectMapper.registerModule(module);
            objectMapper.addMixIn(Position.class, LaurelDriver.DafnyJsonPosition.class);

            var annotationCompiler = context.get(VerifyAnnotationCompiler.class);
            var methodStatusses = annotationCompiler.getMethodStatusPerUri();
            StringBuilder exceptionOutput = new StringBuilder();
            Wrapper<Integer> performanceTicks = new Wrapper<>(null);
            Wrapper<Integer> failedAssertionsCount = new Wrapper<>(0);
            dafnyOutput.lines().forEach(line -> {
                Matcher matcher;
                if (!exceptionOutput.isEmpty()) {
                    exceptionOutput.append(line).append("\n");
                } else if (line.isBlank()) {
                    //noinspection UnnecessaryReturnStatement
                    return;  // nothing to do
                } else if (line.startsWith("{")) {
                    try {
                        DafnyOutput output = objectMapper.readValue(line, DafnyOutput.class);
                        switch (output) {
                            case DafnyDiagnostic dafnyDiagnostic -> {
                                if (dafnyDiagnostic.defaultFormatMessage.contains("[internal error]")) {
                                    throw new RuntimeException("JVerify had an internal exception when calling Dafny: " + dafnyDiagnostic.getMessage(Locale.ENGLISH));
                                }
                                for (var index = 0; index < dafnyDiagnostic.arguments.length; index++) {
                                    dafnyDiagnostic.arguments[index] = nameCompiler.safeGetOriginalName(dafnyDiagnostic.arguments[index]);
                                }

                                if (dafnyDiagnostic.location.filename().contentEquals("additional.dfy")) {
                                    throw new RuntimeException("error in additional.dfy:" + dafnyDiagnostic.getMessage(Locale.ENGLISH));
                                }

                                failedAssertionsCount.setValue(failedAssertionsCount.getValue() + 1);
                                dafnyDiagnostic.flattenRelated().forEach(diagnostics::add);

                                var relativeUri = dafnyDiagnostic.getSource();
                                if (relativeUri == null) {
                                    return;
                                }

                                if (context.get(JavaToDafnyCompiler.class).isContractSource(relativeUri)) {
                                    return;
                                }

                                var uriMethods = methodStatusses.get(relativeUri);
                                var failedJavaMethod = uriMethods.findAtPoint((int) dafnyDiagnostic.getLineNumber());
                                if (failedJavaMethod != null) {
                                    failedJavaMethod.setVerificationStatus(JavaMethodVerificationStatus.VerificationStatus.Failed);
                                }
                            }
                            case StatusMessage statusMessage -> {
                                if ((matcher = dafnySummaryPattern.matcher(statusMessage.getValue().trim())).matches()) {
                                    if (performanceTicks.getValue() != null) {
                                        throw new RuntimeException("Dafny output contains multiple summary lines");
                                    }
                                    performanceTicks.setValue(Integer.parseInt(matcher.group("VerifiedCount")));
                                }
                                if ((matcher = timePattern.matcher(statusMessage.getValue().trim())).matches()) {
                                    options.printTime(matcher.group("Name"), Duration.ofMillis(Long.parseLong(matcher.group("Duration"))));
                                }
                            }
                        }
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException("Malformed Dafny JSON diagnostic: " + line, e);
                    }
                } else {
                    exceptionOutput.append(line).append("\n");
                }
            });
            if (!exceptionOutput.isEmpty()) {
                String diagnosticsString = diagnostics.stream().map(Object::toString).collect(Collectors.joining("\n"));
                throw new RuntimeException("Could not parse Laurel output: " + exceptionOutput + "\n" + diagnosticsString);
            }

            int verifiedCount = 0;
            int skippedCount = 0;
            int failedCount = 0;
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

            int dafnyExitCode = process.waitFor();
            var exitCode = getExitCodeFromDafny(dafnyExitCode);
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

}
