package com.aws.jverify.verifier;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.aws.jverify.JVerify;
import com.aws.jverify.common.Common;
import org.checkerframework.checker.nullness.qual.Nullable;
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

    @Option(names = "--filter-position", description = "Filter what gets verified based on a source location. The location is specified as a file path suffix, optionally followed by a colon and a line number or line range. For example, `jverify verify source1.java source2.java --filter-position=source1.dfy:5-7` will only verify things that between (and including) line 5 and 7 in the file `source1.dfy`. You can also use `:5`, `:5-`, `:-5` to specify individual lines or open ranges.")
    private String filterPositionString;
    
    @Option(names = "--dafny", description = "Location of the Dafny CLI to use. Overrides environment variable JVERIFY_DAFNY.")
    private Path customDafny;

    @Option(names = "--verify-by-default", description = "Whether to verify code without @Verify(true). Defaults to true.", defaultValue = "true")
    private boolean verifyByDefault;

    @Option(names = "--builtin-contracts", description = "Whether to include built-in contracts for the Java standard library", defaultValue = "true")
    private boolean builtinContracts;

    @Option(names = "--verbose", description = "")
    private boolean verbose;

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
        var positionFilter = PositionFilter.getPositionFilter(filterPositionString);
        var verifierOptions = new VerifierOptions(workingDirectory, dafnyPath, jars, 
                tempFile.toPath(), testDafnyVersion,
                printDafny, printBinaryDafny, showRanges, builtinContracts, 
                paths, new String[0], verifyByDefault, false, 
                positionFilter);
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
            if (verbose) {
                System.out.println("Could not find a file at Dafny path '" + dafnyPath + "'");
            }
            dafnyPath = Path.of("dafny");
        }
        return dafnyPath;
    }
}

public record PositionFilter(@Nullable String fileEnding, int start, int end) {

    public static PositionFilter getPositionFilter(String filterPosition) {
        Pattern p = Pattern.compile("(.*)(?::(\\d*)(-?)(\\d*))?$");
        Matcher matcher = p.matcher(filterPosition);

        if (!matcher.find()) {
            throw new RuntimeException("");
        }

        var filePart = matcher.group(1);
        String lineStart = matcher.group(2);
        boolean hasRange = Objects.equals(matcher.group(3), "-");
        String lineEnd = matcher.group(3);

        int start = Integer.MIN_VALUE;
        int end = Integer.MAX_VALUE;
        if (!lineEnd.isEmpty()) {
            end = Integer.parseInt(lineEnd);
            if (!hasRange) {
                start = end;
            }
        }
        if (!lineStart.isEmpty()) {
            start = Integer.parseInt(lineStart);
        }
        return new PositionFilter(filePart, start ,end);
    }
}

