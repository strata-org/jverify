package com.aws.jverify.verifier;

import com.sun.tools.javac.util.*;
import picocli.CommandLine;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    public static int verifyJavaFiles(List<JavaFileObject> readFiles, VerifierOptions verifierOptions, Writer output) throws IOException {
        var context = new Context();
        var compiler = new JavaToDafnyCompiler(context);
        var messages = JavacMessages.instance(context);
        messages.add("com.aws.jverify.messages");

        var dafnyEquivalent = compiler.analyzeJavaCode(verifierOptions, readFiles);
        var hasErrors = false;
        for(var diagnostic : compiler.diagnostics.getDiagnostics()) {
            output.write(formatDiagnostic(diagnostic) + "\n");
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                hasErrors = true;
            }
        }
        if (dafnyEquivalent != null && !hasErrors) {
            var programBuilder = new StringBuilder();
            new Serializer(new TextEncoder(programBuilder)).serialize(dafnyEquivalent);
            var program = programBuilder.toString();
            if (verifierOptions.printBinaryDafny() != null) {
                Files.writeString(verifierOptions.printBinaryDafny(), program);
            }
            return runDafnyProcess(program, verifierOptions, output);
        }
        return CommandLine.ExitCode.USAGE;
    }

    private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        StringBuilder sb = new StringBuilder();

        if (diagnostic.getSource() != null) {
            sb.append(diagnostic.getSource().getName());
        }

        sb.append("(");
        sb.append(diagnostic.getLineNumber()).append(":");
        sb.append(diagnostic.getColumnNumber());

        if (diagnostic instanceof JCDiagnostic jcDiagnostic) {
            var endLine = diagnostic.getLineNumber();
            var endColumn = diagnostic.getColumnNumber() + 1;
            sb.append("-").append(endLine).append(":").append(endColumn);
        }

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

        sb.append(diagnostic.getMessage(null));
        return sb.toString();
    }
    
    public static int runDafnyProcess(String program, VerifierOptions verifierOptions, Writer output) {
        var dafnyPath = verifierOptions.dafnyPath();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    dafnyPath.toString(),  // Program path
                    "verify",                       // First argument
                    "--library",
                    verifierOptions.additionalDafnyFile().toAbsolutePath().toString(),
                    "--allow-axioms",
                    "--dont-verify-dependencies",
                    "--input-format",
                    "Binary",
                    "--stdin",                        // Second argument
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

            Process process = processBuilder.start();

            var writer = new OutputStreamWriter(process.getOutputStream());
            writer.write(program);
            writer.close();

            
            // Read the output
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                reader.transferTo(output);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                reader.transferTo(output);
            }

            int exitCode = process.waitFor();
            return exitCode;

        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
            return -1;
        }
    }
}
