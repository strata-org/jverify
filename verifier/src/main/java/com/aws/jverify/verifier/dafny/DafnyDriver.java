package com.aws.jverify.verifier.dafny;

import com.aws.jverify.common.Position;
import com.aws.jverify.verifier.*;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.frontend.InstrumentLower;
import com.aws.jverify.verifier.compiler.frontend.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.frontend.TypesWithoutErasure;
import com.aws.jverify.verifier.compiler.simplifications.NameCompiler;
import com.aws.jverify.verifier.compiler.simplifications.VerifyAnnotationCompiler;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JavacMessages;
import picocli.CommandLine;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DafnyDriver implements Driver {

    private final Context context;
    private final VerifierOptions verifierOptions;

    @Override
    public VerifierOptions getVerifierOptions() {
        return verifierOptions;
    }

    public DafnyDriver(VerifierOptions verifierOptions) {
        InstrumentLower.installModification();
        context = new Context();
        TypesWithoutErasure.preRegister(context);
        context.put(VerifierOptions.class, verifierOptions);
        this.verifierOptions = verifierOptions;
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

        var dafnyEquivalent = verifierOptions.time("Compiling Java to Dafny",
                () -> new JavaToDafnyCompiler(context).analyzeJavaCode(verifierOptions, readFiles));

        var hasErrors = false;
        for (var diagnostic : Reporter.instance(context).diagnostics.getDiagnostics()) {
            diagnostics.add(diagnostic);
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                hasErrors = true;
            }
        }
        if (dafnyEquivalent == null || (hasErrors && !verifierOptions.continueOnErrors())) {
            return new JVerifyResults(diagnostics, CommandLine.ExitCode.USAGE, null);
        } else {
            var program = verifierOptions.time("Serializing Dafny AST", () -> {
                var programBuilder = new StringBuilder();
                new Serializer(new TextEncoder(programBuilder)).serialize(dafnyEquivalent);
                return programBuilder.toString();
            });
            if (verifierOptions.printSerializedOutputProgram() != null) {
                Files.createDirectories(verifierOptions.printSerializedOutputProgram().getParent());
                Files.writeString(verifierOptions.printSerializedOutputProgram(), program);
            }
            var results = runDafnyProcess(NameCompiler.instance(context), program);
            results.diagnostics().addAll(0, diagnostics);
            return results;
        }
    }


    private JVerifyResults runDafnyProcess(NameCompiler nameCompiler,
                                           String program) {
        // First check the Dafny version is correct
        checkDafnyVersion();

        var processBuilder = new ProcessBuilder(
                verifierOptions.dafnyPath().toString(),
                "verify",
                "--library",
                verifierOptions.additionalDafnyFile().toAbsolutePath().normalize().toString(),
                "--allow-axioms",
                "--dont-verify-dependencies",
                "--input-format",
                "Binary",
                "--stdin",
                "--json-output",
                "--type-system-refresh",
                "--general-newtypes",
                // "--progress", "Batch",
                //"--check-source-location-consistency",
                "--general-traits=datatype"
        );
        if (verifierOptions.printDafny() != null) {
            processBuilder.command().add("--print=" + verifierOptions.printDafny());
        }
        applyPositionFilter(verifierOptions, processBuilder);
        if (verifierOptions.showRanges()) {
            // --show-snippets has no affect because Dafny can't extract them from the serialized source anyways
            processBuilder.command().add("--show-snippets=false");
            processBuilder.command().add("--print-ranges");
        }
        processBuilder.command().add("--ignore-indentation");
        for (var option : verifierOptions.additionalDafnyArguments()) {
            processBuilder.command().add(option);
        }

        if (verifierOptions.verbose()) {
            verifierOptions.outWriter().println("Dafny options: " + String.join(" ", processBuilder.command()));
        }

        return verifierOptions.time("Running Dafny", () -> {
            try {
                // Redirect stderr into stdout, instead of reading one and then the other,
                // in order to preserve the order of output and to avoid potential deadlock.
                var process = processBuilder.redirectErrorStream(true).start();
                try (var stdin = process.outputWriter()) {
                    stdin.write(program);
                }
                return parseDafnyJsonOutput(verifierOptions, nameCompiler, process);
            } catch (InterruptedException | IOException e) {
                verifierOptions.outWriter().println("Failed to use Dafny at: " + verifierOptions.dafnyPath());
                e.printStackTrace();
                return new JVerifyResults(List.of(), -1, null);
            }
        });
    }
    private static boolean checkedVersion = false;

    private void checkDafnyVersion() {
        if (!verifierOptions.testDafnyVersion()) {
            return;
        }

        if (!checkedVersion) {
            Properties properties = new Properties();
            try (InputStream input = DafnyDriver.class.getClassLoader().getResourceAsStream("com/aws/jverify/dafny.properties")) {
                properties.load(input);
                var dafnyVersion = properties.getProperty("dafnyVersion");
                var dafnyRef = properties.getProperty("dafnyRef");
                var expectedVersion = dafnyVersion + "+" + dafnyRef;

                var processBuilder = new ProcessBuilder(
                        verifierOptions.dafnyPath().toString(),
                        "--version"
                );

                var process = processBuilder.redirectErrorStream(true).start();
                try (var stdout = process.inputReader()) {
                    var output = stdout.lines()
                            .collect(Collectors.joining(""))
                            .trim();
                    if (process.waitFor() != 0) {
                        throw new RuntimeException("dafny --version failed:\n" + output);
                    }
                    // Turned off while we're using a Dafny submodule
                    // Alternatively, we can check whether the submodule version matches the output
                    if (!output.equals(expectedVersion)) {
                        throw new IllegalStateException("Wrong Dafny version: expected " + expectedVersion + " but found " + output
                                + " at location " + verifierOptions.dafnyPath());
                    }
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }

            checkedVersion = true;
        }
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
    private JVerifyResults parseDafnyJsonOutput(VerifierOptions options,
                                                       NameCompiler nameCompiler,
                                                       Process process) throws IOException, InterruptedException {

        List<Diagnostic<?>> diagnostics = new ArrayList<>();
        try (var dafnyOutput = process.inputReader()) {

            var objectMapper = new ObjectMapper();

            SimpleModule module = new SimpleModule();
            module.addDeserializer(DafnyOutput.class, new DafnyOutputDeserializer(objectMapper));
            objectMapper.registerModule(module);
            objectMapper.addMixIn(Position.class, DafnyDriver.DafnyJsonPosition.class);

            var annotationCompiler = context.get(VerifyAnnotationCompiler.class);
            var methodStatusses = annotationCompiler.getMethodStatusPerUri();
            StringBuilder exceptionOutput = new StringBuilder();
            Wrapper<Integer> performanceTicks = new Wrapper<>(null);
            Wrapper<Integer> errorCount = new Wrapper<>(0);
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

                                errorCount.setValue(errorCount.getValue() + 1);
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
                throw new RuntimeException("Could not parse Dafny output: " + exceptionOutput + "\n" + diagnosticsString);
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
                            errorCount.getValue(), skippedCount, performanceTicks.getValue()));
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
