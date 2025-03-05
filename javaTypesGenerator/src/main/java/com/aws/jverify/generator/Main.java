package com.aws.jverify.generator;

import picocli.CommandLine;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

public class Main {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new AppCommand()).execute(args);
        System.exit(exitCode);
    }
}

@Command(name = "verify", description = "Verify Java code that calls the JVerify library")
class AppCommand implements Callable<Integer> {
    @Parameters(description = "Dafny cs-schema file")
    private Path dafnySchemaFile;

    @Parameters(description = "Directory to place the generated Java files")
    private Path outputDirectory;

    @Override
    public Integer call() throws IOException {
        String input = Files.readString(dafnySchemaFile.toAbsolutePath());
        new CSharpToJavaConverter("com.aws.jverify.generated").writeJava(input, outputDirectory.toAbsolutePath());
        return CommandLine.ExitCode.OK;
    }
}