package com.aws.jverify.verifier.dafny;

import com.aws.jverify.common.Common;
import com.aws.jverify.common.Position;
import com.aws.jverify.verifier.*;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.frontend.InstrumentLower;
import com.aws.jverify.verifier.compiler.frontend.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.frontend.TypesWithoutErasure;
import com.aws.jverify.verifier.compiler.simplifications.NameCompiler;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.JavacMessages;
import picocli.CommandLine;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DafnyDriver implements Driver {

    public static Context context;
    public record VerificationResultsWithIntervalTreeMap(DafnyVerificationResults verificationResults, HashMap<URI, IntervalTree<Integer,
            JavaMethodVerificationStatus>> sourceFileToMethodIntervals) {}

    public JVerifyResults verifyJavaFile(JavaFileObject javaFile, VerifierOptions options)
            throws IOException {
        return verifyJavaFiles(List.of(javaFile), options);
    }
    
    void foo() {
        
//            .flatMap(diagnostic -> diagnostic instanceof DafnyDiagnostic dafnyDiagnostic
//        ? dafnyDiagnostic.flattenRelated()
//        : Stream.of(diagnostic))
//        // Remove diagnostics from "additional.dfy" file as they cannot be checked now by
//        // our test engine. And we probably want to localize them elsewhere anyway
//        .filter(d -> {
//            if (d instanceof DafnyDiagnostic dafnyDiagnostic) {
//                if (dafnyDiagnostic.location.filename().contentEquals("additional.dfy")) {
//                    throw new RuntimeException("error in additional.dfy:" + dafnyDiagnostic.getMessage(Locale.ENGLISH));
//                }
//                return true;
//            }
//            return true;
//        })

//        if (expectedJavaVerifiedCount != null) {
//            assert verificationResults.sourceFileToMethodIntervals() != null;
//            assertThat("Java verified method count",
//                    verificationResults.sourceFileToMethodIntervals().values().stream()
//                            .flatMap(IntervalTree::streamNodes)
//                            .filter(node -> node.getValue().getVerificationStatus()
//                                    .equals(JavaMethodVerificationStatus.VerificationStatus.Verified))
//                            .toList().size(),
//                    is(expectedJavaVerifiedCount));
//        }
    }
    
    public JVerifyResults verifyJavaFiles(
            List<JavaFileObject> readFiles,
            VerifierOptions verifierOptions
    ) throws IOException {
        List<Diagnostic<?>> diagnostics = new ArrayList<>();

        InstrumentLower.installModification();
        context = new Context();
        TypesWithoutErasure.preRegister(context);
        context.put(VerifierOptions.class, verifierOptions);

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
            var results = runDafnyProcess(NameCompiler.instance(context), program, verifierOptions);
            results.diagnostics().addAll(0, diagnostics);
            return results;
        }
    }

    public int verifyJavaFilesExit(
            List<JavaFileObject> readFiles,
            VerifierOptions verifierOptions
    ) throws IOException {
        var results = verifyJavaFiles(readFiles, verifierOptions);
        outputVerificationResults(results, verifierOptions, verifierOptions.outWriter());
        verifierOptions.outWriter().flush();
        return results.exitCode();
    }


    private static void outputVerificationResults(JVerifyResults results,
                                                  VerifierOptions verifierOptions, Writer outputWriter) throws IOException {

        for (var diagnostic : results.diagnostics()) {
            outputWriter.write(formatDiagnostic(verifierOptions.showFilepaths(), diagnostic));
            outputWriter.write('\n');
        }
        
//        for (var dafnyOutput : results.verificationResults.getOutputs()) {
//            if (dafnyOutput instanceof DafnyDiagnostic dafnyDiagnostic) {
//                outputWriter.write(formatDiagnostic(verifierOptions.showFilepaths(), dafnyDiagnostic));
//                outputWriter.write('\n');
//                if (dafnyDiagnostic.relatedInformation != null) {
//                    for (var relatedInfo : dafnyDiagnostic.relatedInformation) {
//                        outputWriter.write(formatDiagnostic(verifierOptions.showFilepaths(), relatedInfo.asDiagnostic()));
//                        outputWriter.write('\n');
//                    }
//                }
//                var relativeUri = dafnyDiagnostic.getSource();
//                if (relativeUri == null) {
//                    continue;
//                }
//
//                if (context.get(JavaToDafnyCompiler.class).isContractSource(relativeUri)) {
//                    continue;
//                }
//
//                var uriMethods = results.sourceFileToMethodIntervals.get(relativeUri);
//                var failedJavaMethod = uriMethods.findAtPoint((int) dafnyDiagnostic.getLineNumber());
//                if (failedJavaMethod != null) {
//                    failedJavaMethod.setVerificationStatus(JavaMethodVerificationStatus.VerificationStatus.Failed);
//                }
//            }
//        }
//
//        if (results.verificationResults.getDafnyFinishedMessage() != null) {
//            outputWriter.write(results.verificationResults.getDafnyFinishedMessage());
//        }
//
//        /*
//         * checks for the following:
//         *  - the presence of non-library methods,
//         *  - lack of errors in generated Dafny code
//         *  - Dafny did not fail to run
//         *  - lack of errors in Dafny run
//         */
//        if (results.sourceFileToMethodIntervals != null //there exists
//                && results.verificationResults.getExitCode() != CommandLine.ExitCode.USAGE
//                && results.verificationResults.getExitCode() != -1
//                && results.verificationResults.getExitCode() != getExitCodeFromDafny(2)
//        ) {
//            outputWriter.write('\n');
//            String bullet = "• ";
//
//            var attemptedCount = results.sourceFileToMethodIntervals().values().stream()
//                    .flatMap(IntervalTree::streamNodes).count();
//            outputWriter.write(String.format("Found %s verifiable Java method%s", attemptedCount, Common.getExtraS((int) attemptedCount)));
//            outputWriter.write('\n');
//
//            var skippedCount = results.sourceFileToMethodIntervals().values().stream()
//                    .flatMap(IntervalTree::streamNodes)
//                    .filter(node -> node.getValue().getVerificationStatus()
//                            .equals(JavaMethodVerificationStatus.VerificationStatus.Skipped))
//                    .toList().size();
//            if (skippedCount > 0) {
//                outputWriter.write(String.format("%sSkipped: %s",bullet, skippedCount));
//                outputWriter.write('\n');
//            }
//
//            var verifiedCount = results.sourceFileToMethodIntervals().values().stream()
//                    .flatMap(IntervalTree::streamNodes)
//                    .filter(node -> node.getValue().getVerificationStatus()
//                            .equals(JavaMethodVerificationStatus.VerificationStatus.Verified))
//                    .toList().size();
//            if (verifiedCount > 0) {
//                outputWriter.write(String.format("%sVerified: %s",bullet, verifiedCount));
//                outputWriter.write('\n');
//            }
//
//            var failedCount = results.sourceFileToMethodIntervals().values().stream()
//                    .flatMap(IntervalTree::streamNodes)
//                    .filter(node -> node.getValue().getVerificationStatus()
//                            .equals(JavaMethodVerificationStatus.VerificationStatus.Failed))
//                    .toList().size();
//            if (failedCount > 0) {
//                outputWriter.write(String.format("%sFailed: %s", bullet, failedCount));
//                outputWriter.write('\n');
//            }
//        }
    }

    private static String formatDiagnostic(boolean filePath, Diagnostic<?> diagnostic) {
        var sb = new StringBuilder();

        if (diagnostic instanceof JCDiagnostic jcDiagnostic) {
            sb.append(jcDiagnostic.getSource().getName());

            var line = diagnostic.getLineNumber();
            var column = diagnostic.getColumnNumber();
            sb.append("(");
            sb.append(line).append(":").append(column);
            sb.append("-");
            sb.append(line).append(":").append(column + 1);
            sb.append("): ");
        } else if (diagnostic instanceof DafnyDiagnostic dafnyDiagnostic) {
            var filePart = filePath ? dafnyDiagnostic.location.filePath() :dafnyDiagnostic.location.filename(); 
            sb.append(filePart)
                    .append("(")
                    .append(dafnyDiagnostic.getRange())
                    .append("): ");
        } else {
            throw new IllegalArgumentException(
                    "Formatting not implemented for diagnostic type " + diagnostic.getClass().getName());
        }

        sb.append(Driver.formatMessage(diagnostic));
        return sb.toString();
    }

    private static boolean checkedVersion = false;

    private static void checkDafnyVersion(VerifierOptions verifierOptions) {
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

    public static JVerifyResults runDafnyProcess(NameCompiler nameCompiler,
                                       String program,
                                       VerifierOptions verifierOptions) {
        // First check the Dafny version is correct
        checkDafnyVersion(verifierOptions);

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
    private static JVerifyResults parseDafnyJsonOutput(VerifierOptions options, 
                                             NameCompiler nameCompiler,
                                             Process process) throws IOException, InterruptedException {

        List<Diagnostic<?>> diagnostics = new ArrayList<>();
        try (var dafnyOutput = process.inputReader()) {

            var objectMapper = new ObjectMapper();

            SimpleModule module = new SimpleModule();
            module.addDeserializer(DafnyOutput.class, new DafnyOutputDeserializer(objectMapper));
            objectMapper.registerModule(module);
            objectMapper.addMixIn(Position.class, DafnyJsonPosition.class);

            StringBuilder exceptionOutput = new StringBuilder();
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
                                diagnostics.add(dafnyDiagnostic);
                            }
                            case StatusMessage statusMessage -> {
                                if ((matcher = dafnySummaryPattern.matcher(statusMessage.getValue().trim())).matches()) {
//                                    if (outResults.getDafnyVerifiedCount() != null) {
//                                        throw new RuntimeException("Dafny output contains multiple summary lines");
//                                    }
                                    //outResults.setDafnyVerifiedCount(Integer.parseInt(matcher.group("VerifiedCount")));
                                    //outResults.setDafnyErrorCount(Integer.parseInt(matcher.group("ErrorCount")));
                                    //outResults.setDafnyFinishedMessage(statusMessage.getValue());
                                }
                                if ((matcher = timePattern.matcher(statusMessage.getValue().trim())).matches()) {
                                    options.printTime(matcher.group("Name"), Duration.ofMillis(Long.parseLong(matcher.group("Duration"))));
                                }
                            }
                        }
                        //outResults.getOutputs().add(output);
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
            
            int dafnyExitCode = process.waitFor();
            var exitCode = getExitCodeFromDafny(dafnyExitCode);
            return new JVerifyResults(diagnostics, exitCode, null);
        }
        
    }

    /**
     * Used as an {@link ObjectMapper} mixin when parsing {@link Position},
     * since Dafny JSON diagnostics include a {@code pos} field that we don't use or need.
     */
    @JsonIgnoreProperties({"pos"})
    private static abstract class DafnyJsonPosition {}

}
