package com.aws.jverify.verifier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.tools.javac.util.*;
import picocli.CommandLine;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Driver {

    public static int verifyJavaSource(VerifierOptions options, String source, Writer output) throws IOException {
        var files = new ArrayList<JavaFileObject>();
        files.add(new SourceFile("file:/test.java", source));
        return verifyJavaFiles(files, options, output);
    }
    
    public static int verifyJavaExample(VerifierOptions options, Path javaFile, Writer output) throws IOException {
        var files = new ArrayList<JavaFileObject>();
        files.add(new SourceFile(javaFile, Files.readString(javaFile)));
        return verifyJavaFiles(files, options, output);
    }

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

    public static int verifyJavaFiles(
            List<JavaFileObject> readFiles,
            VerifierOptions verifierOptions,
            Consumer<Diagnostic<?>> diagnosticConsumer
    ) throws IOException {
        var context = new Context();
        var compiler = new JavaToDafnyCompiler(context);
        var messages = JavacMessages.instance(context);
        messages.add("com.aws.jverify.messages");

        var dafnyEquivalent = compiler.analyzeJavaCode(verifierOptions, readFiles);
        var hasErrors = false;
        for (var diagnostic : compiler.diagnostics.getDiagnostics()) {
            diagnosticConsumer.accept(diagnostic);
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                hasErrors = true;
            }
        }
        if (dafnyEquivalent == null || hasErrors) {
            return CommandLine.ExitCode.USAGE;
        }

        var programBuilder = new StringBuilder();
        new Serializer(new TextEncoder(programBuilder)).serialize(dafnyEquivalent);
        var program = programBuilder.toString();
        if (verifierOptions.printBinaryDafny() != null) {
            Files.writeString(verifierOptions.printBinaryDafny(), program);
        }
        return runDafnyProcess(program, verifierOptions, diagnosticConsumer::accept);
    }

    public static int verifyJavaFiles(
            List<JavaFileObject> readFiles,
            VerifierOptions verifierOptions,
            Writer output
    ) throws IOException {
        return verifyJavaFiles(readFiles, verifierOptions, diagnostic -> {
            try {
                output.write(formatDiagnostic(diagnostic));
                output.write('\n');
                if (diagnostic instanceof DafnyDiagnostic dafnyDiagnostic
                        && dafnyDiagnostic.relatedInformation != null) {
                    for (var relatedInfo : dafnyDiagnostic.relatedInformation) {
                        output.write(formatDiagnostic(relatedInfo.asDiagnostic()));
                        output.write('\n');
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Exception while writing verification output", e);
            }
        });
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

            switch (diagnostic.getKind()) {
                case ERROR:
                    sb.append("error: ");
                    break;
                case WARNING:
                    sb.append("warning: ");
                    break;
                case MANDATORY_WARNING:
                    sb.append("required Warning: ");
                    break;
                case NOTE:
                    sb.append("note: ");
                    break;
                default:
                    sb.append(diagnostic.getKind()).append(": ");
            }
        } else if (diagnostic instanceof DafnyDiagnostic dafnyDiagnostic) {
            if (dafnyDiagnostic.location != null) {
                sb.append(dafnyDiagnostic.getSource());

                var start = dafnyDiagnostic.location.range().start();
                var end = dafnyDiagnostic.location.range().end();
                sb.append("(");
                sb.append(start.line()).append(":").append(start.character());
                sb.append("-");
                sb.append(end.line()).append(":").append(end.character());
                sb.append("): ");
            }
        } else {
            throw new IllegalArgumentException(
                    "Formatting not implemented for diagnostic type " + diagnostic.getClass().getName());
        }

        sb.append(diagnostic.getMessage(null));
        return sb.toString();
    }

    public static int runDafnyProcess(String program, VerifierOptions verifierOptions, Consumer<DafnyDiagnostic> diagnosticConsumer) {
        var dafnyPath = verifierOptions.dafnyPath();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    dafnyPath.toString(),  // Program path
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
            
            for(var option : verifierOptions.additionalDafnyArguments()) {
                processBuilder.command().add(option);
            }

            // Redirect stderr into stdout, instead of reading one and then the other,
            // in order to preserve the order of output and to avoid potential deadlock.
            var process = processBuilder.redirectErrorStream(true).start();
            try (var stdin = new OutputStreamWriter(process.getOutputStream())) {
                stdin.write(program);
            }
            try (var stdout = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                parseDafnyJsonOutput(stdout).forEach(diagnosticConsumer);
                return process.waitFor();
            }
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    private static final Pattern dafnySummaryPattern = Pattern.compile(
            "Dafny program verifier finished with \\d+ verified, \\d+ errors?");

    /**
     * Parses the given {@code dafny verify} output into a stream of diagnostics,
     * including a diagnostic of the verification summary ("Dafny program verifier finished with ...").
     * Note that Dafny must be invoked with {@code --json-diagnostics} or else parsing will fail.
     */
    private static Stream<DafnyDiagnostic> parseDafnyJsonOutput(BufferedReader dafnyOutput) {
        var objectMapper = new ObjectMapper();
        return dafnyOutput.lines().flatMap(line -> {
            if (line.isBlank()) {
                return Stream.empty();
            } else if (line.startsWith("{")) {
                final DafnyDiagnostic diagnostic;
                try {
                    diagnostic = objectMapper.readValue(line, DafnyDiagnostic.class);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Malformed Dafny JSON diagnostic: " + line, e);
                }
                return Stream.of(diagnostic);
            } else if (dafnySummaryPattern.matcher(line).matches()) {
                return Stream.of(DafnyDiagnostic.forSummary(line));
            } else {
                throw new RuntimeException("Could not parse line of Dafny output: " + line);
            }
        });
    }
}
