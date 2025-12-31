package com.aws.jverify.verifier.laurel;

import com.amazon.ion.IonSystem;
import com.amazon.ion.system.IonSystemBuilder;
import com.aws.jverify.common.Common;
import com.aws.jverify.common.Position;
import com.aws.jverify.laurel.IonSerializer;
import com.aws.jverify.laurel.Node;
import com.aws.jverify.verifier.*;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.frontend.InstrumentLower;
import com.aws.jverify.verifier.compiler.frontend.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.frontend.TypesWithoutErasure;
import com.aws.jverify.verifier.compiler.generator.laurel.JavaToLaurelCompiler;
import com.aws.jverify.verifier.compiler.simplifications.NameCompiler;
import com.aws.jverify.verifier.compiler.simplifications.VerifyAnnotationCompiler;
import com.aws.jverify.verifier.dafny.DafnyOutput;
import com.aws.jverify.verifier.dafny.DafnyVerificationResults;
import com.aws.jverify.verifier.dafny.Driver;
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
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LaurelDriver implements Driver {

    public static Context context;

    public VerificationResultsWithIntervalTreeMap verifyJavaFile(JavaFileObject javaFile, VerifierOptions options)
            throws IOException {
        return verifyJavaFiles(List.of(javaFile), options);
    }
    
    public VerificationResultsWithIntervalTreeMap verifyJavaFiles(
            List<JavaFileObject> readFiles,
            VerifierOptions verifierOptions
    ) throws IOException {
        var verificationResults = new DafnyVerificationResults();

        InstrumentLower.installModification();
        context = new Context();
        TypesWithoutErasure.preRegister(context);
        context.put(VerifierOptions.class, verifierOptions);

        var messages = JavacMessages.instance(context);
        messages.add("com.aws.jverify.messages");

        Node dafnyEquivalent = verifierOptions.time("Compiling Java to Dafny",
                () -> new JavaToLaurelCompiler(context).analyzeJavaCode(verifierOptions, readFiles));

        var hasErrors = false;
        for (var diagnostic : Reporter.instance(context).diagnostics.getDiagnostics()) {
            verificationResults.getJverifyDiagnostics().add(diagnostic);
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                hasErrors = true;
            }
        }
        if (dafnyEquivalent == null || (hasErrors && !verifierOptions.continueOnErrors())) {
            verificationResults.setExitCode(CommandLine.ExitCode.USAGE);
            return new VerificationResultsWithIntervalTreeMap(verificationResults, null);
        } else {
            var program = verifierOptions.time("Serializing Dafny AST", () -> {
                var programBuilder = new StringBuilder();
                IonSystem ionSystem = IonSystemBuilder.standard().build();
                new IonSerializer(ionSystem).serialize(dafnyEquivalent);
                return programBuilder.toString();
            });
            if (verifierOptions.printSerializedOutputProgram() != null) {
                Files.createDirectories(verifierOptions.printSerializedOutputProgram().getParent());
                Files.writeString(verifierOptions.printSerializedOutputProgram(), program);
            }
            runDafnyProcess(NameCompiler.instance(context), program, verifierOptions, verificationResults);
            return new VerificationResultsWithIntervalTreeMap(verificationResults, context.get(VerifyAnnotationCompiler.class)
                    .getSourceFileToMethodIntervalTreeMap());
        }
    }

    public int verifyJavaFilesExit(
            List<JavaFileObject> readFiles,
            VerifierOptions verifierOptions
    ) throws IOException {
        var verificationResultsWithIntervalTreeMap = verifyJavaFiles(readFiles, verifierOptions);
        outputVerificationResults(verificationResultsWithIntervalTreeMap, verifierOptions, verifierOptions.outWriter());
        verifierOptions.outWriter().flush();
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

                if (context.get(JavaToDafnyCompiler.class).isContractSource(relativeUri)) {
                    continue;
                }

                var uriMethods = verificationResultsWithIntervalTreeMap.sourceFileToMethodIntervals.get(relativeUri);
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
            try (InputStream input = LaurelDriver.class.getClassLoader().getResourceAsStream("com/aws/jverify/dafny.properties")) {
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
                                       DafnyVerificationResults outResults) {
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

        verifierOptions.time("Running Strata", () -> {
            try {
                // Redirect stderr into stdout, instead of reading one and then the other,
                // in order to preserve the order of output and to avoid potential deadlock.
                var process = processBuilder.redirectErrorStream(true).start();
                try (var stdin = process.outputWriter()) {
                    stdin.write(program);
                }
                try (var stdout = process.inputReader()) {
                    // TODO parse output
                    int dafnyExitCode = process.waitFor();
                    var exitCode = getExitCodeFromDafny(dafnyExitCode);
                    outResults.setExitCode(exitCode);
                }
            } catch (InterruptedException | IOException e) {
                verifierOptions.outWriter().println("Failed to use Dafny at: " + verifierOptions.dafnyPath());
                e.printStackTrace();
                outResults.setExitCode(-1);
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
     * Used as an {@link ObjectMapper} mixin when parsing {@link Position},
     * since Dafny JSON diagnostics include a {@code pos} field that we don't use or need.
     */
    @JsonIgnoreProperties({"pos"})
    private static abstract class DafnyJsonPosition {}

}
