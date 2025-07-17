package com.aws.jverify.verifier;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import com.aws.jverify.JVerify;
import com.aws.jverify.common.Common;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

public class Main {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new AppCommand()).execute(args);
        System.exit(exitCode);
    }
}

@Command(name = "verify", description = "Verify Java code that calls the JVerify library")
class AppCommand implements Callable<Integer> {
    @Spec
    CommandSpec spec;
    
    @Parameters(description = "Java files to verify", arity = "1..*")
    private List<Path> inputs;
    
    @Option(names = "--print-binary-dafny", description = "Given a filepath, prints the binary Dafny code that is generated from Java")
    private Path printBinaryDafny;

    @Option(names = "--jar", description = "Includes this jar file on the classpath", arity = "0..*")
    private List<String> additionalJars;

    @Option(names = "--print-dafny", description = "Given a filepath, prints the Dafny code that is generated from Java")
    private Path printDafny;
    
    @Option(names = "--show-ranges", description = "Show source location ranges in diagnostics")
    private boolean showRanges;
    
    @Option(names = "--paths", description = "Show file paths instead of names")
    private boolean paths;

    @Option(names = "--dafny", description = "Location of the Dafny CLI to use. Overrides environment variable JVERIFY_DAFNY.")
    private Path customDafny;

    @Option(names = "--verify-by-default", description = "Whether to verify code without @Verify(true). Defaults to true.", defaultValue = "true")
    private boolean verifyByDefault;

    @Option(names = "--builtin-contracts", description = "Whether to include built-in contracts for the Java standard library", defaultValue = "true")
    private boolean builtinContracts;

    @Override
    public Integer call() throws IOException {
        Writer writer = spec.commandLine().getOut();

        // In the future we'll have to add an argument to specify jar files for dependencies of the input sources,
        // And those will include the JVerify library jar.
        // But right now we manually find the JVerify library jar and include it in the compilation.
        var jverifyLibraryLocation = Path.of(URI.create(Common.getJarLocationForClass(JVerify.class)));

        File tempFile = File.createTempFile("jverify-prelude-", ".dfy");
        InputStream stream = getClass().getResourceAsStream("/additional.dfy");
        Files.copy(stream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        var dafnyPath = getDafnyPath();
        additionalJars = additionalJars == null ? List.of() : additionalJars;
        List<Path> jars = Stream.concat(additionalJars.stream().flatMap(p -> Arrays.stream(p.split(":")).map(Path::of)), 
                Stream.of(jverifyLibraryLocation)).toList();
        
        var testDafnyVersion = customDafny != null;
        var workingDirectory = Path.of(System.getProperty("user.dir"));
        var verifierOptions = new VerifierOptions(workingDirectory, dafnyPath, jars, tempFile.toPath(), testDafnyVersion,
                printDafny, printBinaryDafny, showRanges, builtinContracts, paths, new String[0], verifyByDefault, true);
        var exitCode = Driver.verifyJavaPaths(inputs, verifierOptions, writer);
        writer.flush();
        return exitCode;
    }

    private Path getDafnyPath() {
        var dafnyPath = customDafny;
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

