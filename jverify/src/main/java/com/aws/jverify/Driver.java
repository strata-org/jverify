package com.aws.jverify;

import javax.tools.JavaFileObject;
import java.io.*;
import java.lang.constant.Constable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Driver {

    public static int verifyJavaExample(String javaCode, Writer output) throws IOException {
        var libraryLocation = "/Users/rwillems/SourceCode/GradleBased/jverify/src/main/java/com/aws/jverify/JVerify.java";
        String library = Files.readString(Path.of(libraryLocation));

        List<JavaFileObject> files = List.of(new SourceFile("test.java", javaCode), new SourceFile(libraryLocation, library));

        var dafnyEquivalent = new JavaToDafnyCompiler().analyzeJavaCode(files);
        var sb = new StringBuilder();
        new Serializer(new TextEncoder(sb)).serialize(dafnyEquivalent);
        var program = sb.toString();
        return runDafnyProcess(program, output);
    }
    

    public static int runDafnyProcess(String program, Writer output) {
        var additionalDafnyPath = "/Users/rwillems/SourceCode/GradleBased/jverify/src/main/java/com/aws/jverify/additional.dfy";
        var dafnyPath = "/Users/rwillems/SourceCode/dafny/Scripts/dafny";
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    dafnyPath,  // Program path
                    "verify",                       // First argument
                    additionalDafnyPath,
                    "--input-format",
                    "Binary",
                    "--stdin"                        // Second argument
            );

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
