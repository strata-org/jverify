package com.aws.jverify.verifier;

import picocli.CommandLine;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        var compiler = new JavaToDafnyCompiler();
        var dafnyEquivalent = compiler.analyzeJavaCode(verifierOptions, readFiles);
        var hasErrors = false;
        for(var diagnostic : compiler.diagnostics.getDiagnostics()) {
            output.write(diagnostic.getMessage(Locale.ENGLISH) + "\n");
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                hasErrors = true;
            }
        }
        new Thread();
        if (dafnyEquivalent != null && !hasErrors) {
            var sb = new StringBuilder();
            new Serializer(new TextEncoder(sb)).serialize(dafnyEquivalent);
            var program = sb.toString();
            if (verifierOptions.printBinaryDafny() != null) {
                Files.writeString(verifierOptions.printBinaryDafny(), program);
            }
            return runDafnyProcess(program, verifierOptions, output);
        }
        return CommandLine.ExitCode.USAGE;
    }
    
    public static int runDafnyProcess(String program, VerifierOptions verifierOptions, Writer output) {
        var dafnyPath = verifierOptions.dafnyPath();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    dafnyPath.toString(),  // Program path
                    "verify",                       // First argument
                    verifierOptions.additionalDafnyFile().toAbsolutePath().toString(),
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
