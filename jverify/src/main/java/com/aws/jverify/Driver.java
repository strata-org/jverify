package com.aws.jverify;

import javax.tools.JavaFileObject;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Driver {

    public static int verifyJavaExample(String javaCode, Writer output) throws IOException {
        var files = new ArrayList<JavaFileObject>();
        files.add(new SourceFile("test.java", javaCode));
        return verifyJavaFiles(files, new VerifierOptions(null, null), output);
    }

    public static int verifyJavaPaths(List<Path> files, VerifierOptions verifierOptions, Writer output) throws IOException {
        List<JavaFileObject> readFiles = files.stream().map((Path p) -> {
            try {
                return new SourceFile(p.toString(), Files.readString(p));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
        return verifyJavaFiles(readFiles, verifierOptions, output);
    }

    public static int verifyJavaFiles(List<JavaFileObject> readFiles, VerifierOptions verifierOptions, Writer output) throws IOException {
        var libraryLocation = "/Users/rwillems/SourceCode/GradleBased/jverify/src/main/java/com/aws/jverify/JVerify.java";
        String library = Files.readString(Path.of(libraryLocation));

        readFiles.add(new SourceFile(libraryLocation, library));

        var dafnyEquivalent = new JavaToDafnyCompiler().analyzeJavaCode(readFiles);
        var sb = new StringBuilder();
        new Serializer(new TextEncoder(sb)).serialize(dafnyEquivalent);
        var program = sb.toString();
        if (verifierOptions.printBinaryDafny() != null) {
            Files.writeString(verifierOptions.printBinaryDafny(), program);
        }
        return runDafnyProcess(program, verifierOptions, output);
    }
    
    public static int runDafnyProcess(String program, VerifierOptions verifierOptions, Writer output) {
        var additionalDafnyPath = "/Users/rwillems/SourceCode/GradleBased/jverify/src/main/java/com/aws/jverify/additional.dfy";
        var dafnyPath = "../dafny/Scripts/dafny";
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    dafnyPath,  // Program path
                    "verify",                       // First argument
                    additionalDafnyPath,
                    "--input-format",
                    "Binary",
                    "--stdin"                        // Second argument
            );
            if (verifierOptions.printDafny() != null) {
                processBuilder.command().add("--print=" + verifierOptions.printDafny());
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
