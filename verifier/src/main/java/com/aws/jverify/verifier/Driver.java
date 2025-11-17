package com.aws.jverify.verifier;

import com.aws.jverify.common.Common;
import com.aws.jverify.common.Position;
import com.aws.jverify.verifier.compiler.frontend.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.frontend.InstrumentLower;
import com.aws.jverify.verifier.compiler.frontend.TypesWithoutErasure;
import com.aws.jverify.verifier.compiler.simplifications.NameCompiler;
import com.aws.jverify.verifier.compiler.simplifications.VerifyAnnotationCompiler;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.sun.tools.javac.util.*;
import picocli.CommandLine;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Driver {

    public static Context context;
    public record VerificationResultsWithIntervalTreeMap(VerificationResults verificationResults, HashMap<URI, IntervalTree<Integer,
            JavaMethodVerificationStatus>> sourceFileToMethodIntervals) {}

    public static int verifyJavaPaths(List<Path> files, VerifierOptions verifierOptions, Writer output) throws IOException {
        List<JavaFileObject> readFiles = files.stream().map((Path p) -> {
            try {
                Path normalized = verifierOptions.workingDirectory().resolve(p).normalize();
                return new SourceFile(normalized, Files.readString(normalized));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
        return verifyJavaFiles(readFiles, verifierOptions, output);
    }

    public static VerificationResultsWithIntervalTreeMap verifyJavaFile(JavaFileObject javaFile, VerifierOptions options)
            throws IOException {
        return verifyJavaFiles(List.of(javaFile), options);
    }

    public static VerificationResultsWithIntervalTreeMap verifyJavaFiles(
            List<JavaFileObject> readFiles,
            VerifierOptions verifierOptions
    ) throws IOException {
        var verificationResults = new VerificationResults();

        InstrumentLower.installModification();
        context = new Context();
        TypesWithoutErasure.preRegister(context);
        context.put(VerifierOptions.class, verifierOptions);

        var messages = JavacMessages.instance(context);
        messages.add("com.aws.jverify.messages");

        var beforeCompilation = Instant.now(); 
        var dafnyEquivalent = new JavaToDafnyCompiler(context).analyzeJavaCode(verifierOptions, readFiles);
        var afterCompilation = Instant.now();
        var compilationDuration = Duration.between(beforeCompilation, afterCompilation);
        System.out.println("Compiling Java to Dafny took " + compilationDuration.toMillis() + " ms");

        var hasErrors = false;
        for (var diagnostic : Reporter.instance(context).diagnostics.getDiagnostics()) {
            verificationResults.getJverifyDiagnostics().add(diagnostic);
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                hasErrors = true;
            }
        }
        if (dafnyEquivalent == null || (hasErrors && !verifierOptions.continueOnErrors())) {
            verificationResults.setExitCode(CommandLine.ExitCode.USAGE);
            return new VerificationResultsWithIntervalTreeMap(verificationResults, new HashMap<>());
        } else {
            var beforeSerialization = Instant.now();
            var programBuilder = new StringBuilder();
            new Serializer(new TextEncoder(programBuilder)).serialize(dafnyEquivalent);
            var program = programBuilder.toString();
            if (verifierOptions.printBinaryDafny() != null) {
                Files.createDirectories(verifierOptions.printBinaryDafny().getParent());
                Files.writeString(verifierOptions.printBinaryDafny(), program);
            }
            var afterSerialization = Instant.now();
            var serializationDuration = Duration.between(beforeSerialization, afterSerialization);
            System.out.println("Serializing Dafny AST took " + serializationDuration.toMillis() + " ms");
            runDafnyProcess(NameCompiler.instance(context), program, verifierOptions, verificationResults);
            return new VerificationResultsWithIntervalTreeMap(verificationResults, context.get(VerifyAnnotationCompiler.class)
                    .getSourceFileToMethodIntervalTreeMap());
        }
    }

    public static int verifyJavaFiles(
            List<JavaFileObject> readFiles,
            VerifierOptions verifierOptions,
            Writer outputWriter
    ) throws IOException {
        var verificationResultsWithIntervalTreeMap = verifyJavaFiles(readFiles, verifierOptions);
        outputVerificationResults(verificationResultsWithIntervalTreeMap, verifierOptions, outputWriter);
        return verificationResultsWithIntervalTreeMap.verificationResults.getExitCode();
    }


    private static void outputVerificationResults(VerificationResultsWithIntervalTreeMap verificationResultsWithIntervalTreeMap,
                                                  VerifierOptions verifierOptions, Writer outputWriter) throws IOException {

        for (var diagnostic : verificationResultsWithIntervalTreeMap.verificationResults.getJverifyDiagnostics()) {
            outputWriter.write(formatDiagnostic(verifierOptions.showFilepaths(), diagnostic));
            outputWriter.write('\n');
        }
        for (var dafnyOutput : verificationResultsWithIntervalTreeMap.verificationResults.getOutputs()) {
            if (dafnyOutput instanceof DafnyDiagnostic dafnyDiagnostic) {
                outputWriter.write(formatDiagnostic(verifierOptions.showFilepaths(), dafnyDiagnostic));
                outputWriter.write('\n');
                if (dafnyDiagnostic.relatedInformation != null) {
                    for (var relatedInfo : dafnyDiagnostic.relatedInformation) {
                        outputWriter.write(formatDiagnostic(verifierOptions.showFilepaths(), relatedInfo.asDiagnostic()));
                        outputWriter.write('\n');
                    }
                }
                var relativeUri = dafnyDiagnostic.getSource();
                if (relativeUri == null) {
                    continue;
                }

                if (context.get(JavaToDafnyCompiler.class).isLibrary(relativeUri)) {
                    continue;
                }

                var uriMethods = verificationResultsWithIntervalTreeMap.sourceFileToMethodIntervals.get(relativeUri);
                if (uriMethods == null) {
                    continue;
                }
                var failedJavaMethod = uriMethods.findAtPoint((int) dafnyDiagnostic.getLineNumber());
                if (failedJavaMethod != null) {
                    failedJavaMethod.setVerificationStatus(JavaMethodVerificationStatus.VerificationStatus.Failed);
                }
            }
        }

        if (verificationResultsWithIntervalTreeMap.verificationResults.getDafnyFinishedMessage() != null) {
            outputWriter.write(verificationResultsWithIntervalTreeMap.verificationResults.getDafnyFinishedMessage());
        }

        /*
         * checks for the following:
         *  - the presence of non-library methods,
         *  - lack of errors in generated Dafny code
         *  - Dafny did not fail to run
         *  - lack of errors in Dafny run
         */
        if (verificationResultsWithIntervalTreeMap.sourceFileToMethodIntervals != null //there exists
                && verificationResultsWithIntervalTreeMap.verificationResults.getExitCode() != CommandLine.ExitCode.USAGE
                && verificationResultsWithIntervalTreeMap.verificationResults.getExitCode() != -1
                && verificationResultsWithIntervalTreeMap.verificationResults.getExitCode() != getExitCodeFromDafny(2)
        ) {
            outputWriter.write('\n');
            String bullet = "• ";

            var attemptedCount = verificationResultsWithIntervalTreeMap.sourceFileToMethodIntervals().values().stream()
                    .flatMap(IntervalTree::streamNodes).count();
            outputWriter.write(String.format("Found %s verifiable Java method%s", attemptedCount, Common.getExtraS((int) attemptedCount)));
            outputWriter.write('\n');

            var skippedCount = verificationResultsWithIntervalTreeMap.sourceFileToMethodIntervals().values().stream()
                    .flatMap(IntervalTree::streamNodes)
                    .filter(node -> node.getValue().getVerificationStatus()
                            .equals(JavaMethodVerificationStatus.VerificationStatus.Skipped))
                    .toList().size();
            if (skippedCount > 0) {
                outputWriter.write(String.format("%sSkipped: %s",bullet, skippedCount));
                outputWriter.write('\n');
            }

            var verifiedCount = verificationResultsWithIntervalTreeMap.sourceFileToMethodIntervals().values().stream()
                    .flatMap(IntervalTree::streamNodes)
                    .filter(node -> node.getValue().getVerificationStatus()
                            .equals(JavaMethodVerificationStatus.VerificationStatus.Verified))
                    .toList().size();
            if (verifiedCount > 0) {
                outputWriter.write(String.format("%sVerified: %s",bullet, verifiedCount));
                outputWriter.write('\n');
            }

            var failedCount = verificationResultsWithIntervalTreeMap.sourceFileToMethodIntervals().values().stream()
                    .flatMap(IntervalTree::streamNodes)
                    .filter(node -> node.getValue().getVerificationStatus()
                            .equals(JavaMethodVerificationStatus.VerificationStatus.Failed))
                    .toList().size();
            if (failedCount > 0) {
                outputWriter.write(String.format("%sFailed: %s", bullet, failedCount));
                outputWriter.write('\n');
            }
        }
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

        sb.append(formatMessage(diagnostic));
        return sb.toString();
    }

    public static String formatMessage(Diagnostic<?> diagnostic) {
        if (diagnostic instanceof JCDiagnostic) {
            var prefix = switch (diagnostic.getKind()) {
                case ERROR -> "error";
                case WARNING -> "warning";
                case MANDATORY_WARNING -> "required warning";
                case NOTE -> "note";
                default -> diagnostic.getKind();
            };
            return prefix + ": " + diagnostic.getMessage(null);
        }

        return diagnostic.getMessage(null);
    }

    private static boolean checkedVersion = false;

    private static void checkDafnyVersion(VerifierOptions verifierOptions) {
        if (!verifierOptions.testDafnyVersion()) {
            return;
        }
        
        if (!checkedVersion) {
            Properties properties = new Properties();
            try (InputStream input = Driver.class.getClassLoader().getResourceAsStream("com/aws/jverify/dafny.properties")) {
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

    public static void runDafnyProcess(NameCompiler nameCompiler,
                                       String program,
                                       VerifierOptions verifierOptions,
                                       VerificationResults outResults) {
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
            System.out.println("Dafny options: " + String.join(" ", processBuilder.command()));
        }

        try {
            // Redirect stderr into stdout, instead of reading one and then the other,
            // in order to preserve the order of output and to avoid potential deadlock.
            var before = Instant.now();
            var process = processBuilder.redirectErrorStream(true).start();
            try (var stdin = process.outputWriter()) {
                stdin.write(program);
            }
            try (var stdout = process.inputReader()) {
                parseDafnyJsonOutput(nameCompiler, stdout, outResults);
                int dafnyExitCode = process.waitFor();
                var exitCode = getExitCodeFromDafny(dafnyExitCode);
                outResults.setExitCode(exitCode);
            }
            var after = Instant.now();
            var duration = Duration.between(before, after);
            System.out.println("Running Dafny took " + duration.toMillis() + " ms");
        } catch (InterruptedException | IOException e) {
            System.out.println("Failed to use Dafny at: " + verifierOptions.dafnyPath());
            e.printStackTrace();
            outResults.setExitCode(-1);
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

    /**
     * Parses the given {@code dafny verify} output,
     * adding both diagnostics and the summary verified/error counts to {@code outResults}.
     * Note that Dafny must be invoked with {@code --json-diagnostics} or else parsing will fail.
     */
    private static void parseDafnyJsonOutput(NameCompiler nameCompiler,
                                             BufferedReader dafnyOutput,
                                             VerificationResults outResults) {
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
                        }
                        case StatusMessage statusMessage -> {
                            if ((matcher = dafnySummaryPattern.matcher(statusMessage.getValue().trim())).matches()) {
                                if (outResults.getDafnyVerifiedCount() != null) {
                                    throw new RuntimeException("Dafny output contains multiple summary lines");
                                }
                                outResults.setDafnyVerifiedCount(Integer.parseInt(matcher.group("VerifiedCount")));
                                outResults.setDafnyErrorCount(Integer.parseInt(matcher.group("ErrorCount")));
                                outResults.setDafnyFinishedMessage(statusMessage.getValue());
                            }
                            if (statusMessage.getValue().trim().startsWith("Time to do")) {
                                System.out.println(statusMessage.getValue());
                            }
                        }
                    }
                    outResults.getOutputs().add(output);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Malformed Dafny JSON diagnostic: " + line, e);
                }
            } else {
                exceptionOutput.append(line).append("\n");
            }
        });
        if (!exceptionOutput.isEmpty()) {
            String diagnostics = outResults.getDiagnostics().map(Object::toString).collect(Collectors.joining("\n"));
            throw new RuntimeException("Could not parse Dafny output: " + exceptionOutput + "\n" + diagnostics);
        }
    }

    /**
     * Used as an {@link ObjectMapper} mixin when parsing {@link Position},
     * since Dafny JSON diagnostics include a {@code pos} field that we don't use or need.
     */
    @JsonIgnoreProperties({"pos"})
    private static abstract class DafnyJsonPosition {}

}
