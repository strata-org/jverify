package com.aws.jverify.verifier;

import com.aws.jverify.JVerify;
import com.aws.jverify.common.Common;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.Callable;
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
    @Parameters(description = "Java files to verify", arity = "1..*")
    private List<Path> inputs;

    @Option(
            names = "--print-binary-dafny",
            description =
                    "Given a filepath, prints the binary Dafny code that is generated from Java")
    private Path printBinaryDafny;

    @Option(
            names = "--print-dafny",
            description = "Given a filepath, prints the Dafny code that is generated from Java")
    private Path printDafny;

    @Option(names = "--show-ranges", description = "Show source location ranges in diagnostics")
    private boolean showRanges;

    @Option(
            names = "--dafny",
            description =
                    "Location of the Dafny CLI to use. Overrides environment variable JVERIFY_DAFNY.")
    private Path dafny;

    @Override
    public Integer call() throws IOException {
        Writer writer = new OutputStreamWriter(System.out);

        // In the future we'll have to add an argument to specify jar files for dependencies of the
        // input sources,
        // And those will include the JVerify library jar.
        // But right now we manually find the JVerify library jar and include it in the compilation.
        var jverifyLibraryLocation =
                Path.of(URI.create(Common.getJarLocationForClass(JVerify.class)));

        File tempFile = File.createTempFile("jverify-prelude-", ".dfy");
        InputStream stream = getClass().getResourceAsStream("/additional.dfy");
        Files.copy(stream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        var dafnyPath = getDafnyPath();
        var exitCode =
                Driver.verifyJavaPaths(
                        inputs,
                        new VerifierOptions(
                                dafnyPath,
                                jverifyLibraryLocation,
                                tempFile.toPath(),
                                printDafny,
                                printBinaryDafny,
                                showRanges,
                                new String[0]),
                        writer);
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
}
