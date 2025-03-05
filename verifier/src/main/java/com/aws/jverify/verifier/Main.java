package com.aws.jverify.verifier;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.concurrent.Callable;

import com.aws.jverify.JVerify;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

public class Main {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new AppCommand()).execute(args);
        System.exit(exitCode);
    }
}

@Command(name = "verify", description = "Verify Java code that calls the JVerify library")
class AppCommand implements Callable<Integer> {
    @Parameters(description = "Java files to verify")
    private List<Path> inputs;
    
    @Option(names = "--print-binary-dafny", description = "Given a filepath, prints the binary Dafny code that is generated from Java")
    private Path printBinaryDafny;

    @Option(names = "--print-dafny", description = "Given a filepath, prints the Dafny code that is generated from Java")
    private Path printDafny;
    
    @Option(names = "--no-snippets", description = "Do not show code snippets for verification errors")
    private boolean noSnippets;

    @Option(names = "--dafny", description = "Location of the Dafny CLI to use. Overrides environment variable JVERIFY_DAFNY.")
    private Path dafny;
    
    @Override
    public Integer call() throws IOException {
        Writer writer = new OutputStreamWriter(System.out);

        // In the future we'll have to add an argument to specify jar files for dependencies of the input sources,
        // And those will include the JVerify library jar.
        // But right now we manually find the JVerify library jar and include it in the compilation.
        var jverifyLibraryLocation = Path.of(URI.create(getJarLocationForClass(JVerify.class)).getPath());

        File tempFile = File.createTempFile("jverify-prelude-", ".dfy");
        InputStream stream = getClass().getResourceAsStream("/additional.dfy");
        Files.copy(stream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        var dafnyPath = getDafnyPath();
        var exitCode = Driver.verifyJavaPaths(inputs, new VerifierOptions(dafnyPath, jverifyLibraryLocation, tempFile.toPath(),
                printDafny, printBinaryDafny, noSnippets), writer);
        writer.flush();
        System.exit(exitCode);
        return 0;
    }

    private Path getDafnyPath() {
        var dafnyPath = dafny;
        if (dafnyPath == null || !Files.exists(dafnyPath)) {
            if (System.getenv("JVERIFY_DAFNY") != null) {
                dafnyPath = Path.of(System.getenv("JVERIFY_DAFNY"));
            }
        }
        if (dafnyPath == null || !Files.exists(dafnyPath)) {
            dafnyPath = Path.of("dafny");
        }
        return dafnyPath;
    }


    public static String getJarLocationForClass(Class<?> clazz) {
        try {
            ProtectionDomain protectionDomain = clazz.getProtectionDomain();
            CodeSource codeSource = protectionDomain.getCodeSource();

            if (codeSource != null) {
                URL location = codeSource.getLocation();
                return location.toString();
            } else {
                // For JDK classes, we might need a different approach
                String resourcePath = "/" + clazz.getName().replace('.', '/') + ".class";
                URL resource = clazz.getResource(resourcePath);

                if (resource != null) {
                    String path = resource.toString();

                    // If it's in a jar, the URL will contain "jar:file:"
                    if (path.startsWith("jar:file:")) {
                        // Extract the path to the jar file
                        path = path.substring(9, path.lastIndexOf('!'));
                        return "file:" + path;
                    }
                    return path;
                }
            }

            return "Cannot determine location";
        } catch (Exception e) {
            return "Error determining location: " + e.getMessage();
        }
    }
}

