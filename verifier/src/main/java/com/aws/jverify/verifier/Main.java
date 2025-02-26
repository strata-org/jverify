package com.aws.jverify.verifier;

import java.io.*;
import java.nio.file.Path;
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
    @Parameters(description = "Java files to verify")
    private List<Path> inputs;
    
    @Option(names = "--print-binary-dafny", description = "Print the binary Dafny code that is generated from Java")
    private Path printBinaryDafny;

    @Option(names = "--print-dafny", description = "Print the Dafny code that is generated from Java")
    private Path printDafny;
    
    @Option(names = "--no-snippets", description = "Do not show code snippets for verification errors")
    private boolean noSnippets;
    
    @Override
    public Integer call() throws IOException {
        // This executes when no subcommand is specified
        Writer writer = new OutputStreamWriter(System.out);
        var dafnyPath = "/Users/rwillems/SourceCode/GradleBased/dafny/Scripts/dafny";
        var exitCode = Driver.verifyJavaPaths(inputs, new VerifierOptions(dafnyPath, printDafny, printBinaryDafny, noSnippets), writer);
        writer.flush();
        System.exit(exitCode);
        return 0;
    }
}

