package com.aws.jverify.verifier;

import com.aws.jverify.common.Position;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.tools.javac.util.*;
import org.checkerframework.checker.nullness.qual.Nullable;
import picocli.CommandLine;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Driver {
    public static int verifyJavaPaths(List<Path> files, VerifierOptions verifierOptions, Writer output) throws IOException {
        List<JavaFileObject> readFiles = files.stream().map((Path p) -> {
            try {
                return new SourceFile(p, Files.readString(p));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
        return verifyJavaFiles(readFiles, verifierOptions, output);
    }

    public static VerificationResults verifyJavaFile(JavaFileObject javaFile, VerifierOptions options)
            throws IOException {
        return verifyJavaFiles(List.of(javaFile), options);
    }

    public static VerificationResults verifyJavaFiles(
            List<JavaFileObject> readFiles,
            VerifierOptions verifierOptions
    ) throws IOException {
        var verificationResults = new VerificationResults();

        var context = new Context();
        var compiler = new JavaToDafnyCompiler(context);
        var messages = JavacMessages.instance(context);
        messages.add("com.aws.jverify.messages");

        var dafnyEquivalent = compiler.analyzeJavaCode(verifierOptions, readFiles);
        var hasErrors = false;
        for (var diagnostic : compiler.diagnostics.getDiagnostics()) {
            verificationResults.diagnostics.add(diagnostic);
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                hasErrors = true;
            }
        }
        if (dafnyEquivalent == null || hasErrors) {
            verificationResults.exitCode = CommandLine.ExitCode.USAGE;
        } else {
            var programBuilder = new StringBuilder();
            new Serializer(new TextEncoder(programBuilder)).serialize(dafnyEquivalent);
            var program = programBuilder.toString();
            if (verifierOptions.printBinaryDafny() != null) {
                Files.writeString(verifierOptions.printBinaryDafny(), program);
            }
            runDafnyProcess(program, verifierOptions, verificationResults);
        }
        return verificationResults;
    }

    public static int verifyJavaFiles(
            List<JavaFileObject> readFiles,
            VerifierOptions verifierOptions,
            Writer output
    ) throws IOException {
        var verificationResults = verifyJavaFiles(readFiles, verifierOptions);
        for (var diagnostic : verificationResults.diagnostics) {
            output.write(formatDiagnostic(diagnostic));
            output.write('\n');
            if (diagnostic instanceof DafnyDiagnostic dafnyDiagnostic
                    && dafnyDiagnostic.relatedInformation != null) {
                for (var relatedInfo : dafnyDiagnostic.relatedInformation) {
                    output.write(formatDiagnostic(relatedInfo.asDiagnostic()));
                    output.write('\n');
                }
            }
        }
        assert verificationResults.dafnyFinishedMessage != null;
        output.write(verificationResults.dafnyFinishedMessage);
        return verificationResults.exitCode;
    }

    public static final class VerificationResults {
        // dummy value to tell when it hasn't been set
        private int exitCode = -999;

        private final List<Diagnostic<?>> diagnostics = new ArrayList<>();

        /**
         * Can be null if verification failed before invoking Dafny.
         */
        private @Nullable Integer dafnyVerifiedCount;

        /**
         * Can be null if verification failed before invoking Dafny.
         */
        private @Nullable Integer dafnyErrorCount;

        /**
         * Can be null if verification failed before invoking Dafny.
         */
        private @Nullable String dafnyFinishedMessage;

        public int getExitCode() {
            return exitCode;
        }

        public List<Diagnostic<?>> getDiagnostics() {
            return diagnostics;
        }

        public @Nullable Integer getDafnyVerifiedCount() {
            return dafnyVerifiedCount;
        }

        public @Nullable Integer getDafnyErrorCount() {
            return dafnyErrorCount;
        }
    }

    private static String formatDiagnostic(Diagnostic<?> diagnostic) {
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
            sb.append(dafnyDiagnostic.getSource())
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

    public static void runDafnyProcess(
            String program, VerifierOptions verifierOptions, VerificationResults outResults) {
        var processBuilder = new ProcessBuilder(
                verifierOptions.dafnyPath().toString(),
                "verify",
                "--library",
                verifierOptions.additionalDafnyFile().toAbsolutePath().toString(),
                "--allow-axioms",
                "--dont-verify-dependencies",
                "--input-format",
                "Binary",
                "--stdin",
                "--json-diagnostics",
                "--allow-warnings"
        );
        if (verifierOptions.printDafny() != null) {
            processBuilder.command().add("--print=" + verifierOptions.printDafny());
        }
        if (verifierOptions.showRanges()) {
            // --show-snippets has no affect because Dafny can't extract them from the serialized source anyways
            processBuilder.command().add("--show-snippets=false");
            processBuilder.command().add("--print-ranges");
        }
        processBuilder.command().add("--ignore-indentation");
        for (var option : verifierOptions.additionalDafnyArguments()) {
            processBuilder.command().add(option);
        }

        try {
            // Redirect stderr into stdout, instead of reading one and then the other,
            // in order to preserve the order of output and to avoid potential deadlock.
            var process = processBuilder.redirectErrorStream(true).start();
            try (var stdin = new OutputStreamWriter(process.getOutputStream())) {
                stdin.write(program);
            }
            try (var stdout = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                parseDafnyJsonOutput(stdout, outResults);
                outResults.exitCode = process.waitFor();
            }
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
            outResults.exitCode = -1;
        }
    }

    private static final Pattern dafnySummaryPattern = Pattern.compile(
            "Dafny program verifier finished with (?<VerifiedCount>\\d+) verified, (?<ErrorCount>\\d+) errors?");

    /**
     * Parses the given {@code dafny verify} output,
     * adding both diagnostics and the summary verified/error counts to {@code outResults}.
     * Note that Dafny must be invoked with {@code --json-diagnostics} or else parsing will fail.
     */
    private static void parseDafnyJsonOutput(BufferedReader dafnyOutput, VerificationResults outResults) {
        var objectMapper = new ObjectMapper();
        objectMapper.addMixIn(Position.class, DafnyJsonPosition.class);

        dafnyOutput.lines().forEach(line -> {
            Matcher matcher;
            if (line.isBlank()) {
                //noinspection UnnecessaryReturnStatement
                return;  // nothing to do
            } else if (line.startsWith("{")) {
                try {
                    outResults.diagnostics.add(objectMapper.readValue(line, DafnyDiagnostic.class));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Malformed Dafny JSON diagnostic: " + line, e);
                }
            } else if ((matcher = dafnySummaryPattern.matcher(line)).matches()) {
                outResults.dafnyVerifiedCount = Integer.parseInt(matcher.group("VerifiedCount"));
                outResults.dafnyErrorCount = Integer.parseInt(matcher.group("ErrorCount"));
                outResults.dafnyFinishedMessage = line;
            } else {
                throw new RuntimeException("Could not parse line of Dafny output: " + line);
            }
        });
    }

    /**
     * Used as an {@link ObjectMapper} mixin when parsing {@link Position},
     * since Dafny JSON diagnostics include a {@code pos} field that we don't use or need.
     */
    @JsonIgnoreProperties({"pos"})
    private static abstract class DafnyJsonPosition {}
}
