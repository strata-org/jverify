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
    
    @Option(names = {"--print-binary-dafny", "--print-ir"}, description = "Given a filepath, prints the intermediate representation (Dafny binary or Laurel Ion) generated from Java")
    private Path printBinaryDafny;

    @Option(names = "--classpath", description = "Includes these paths on the classpath", arity = "0..*")
    private List<String> classPaths;

    @Option(names = "--contract-path", description = "Includes contracts from source code in these paths", arity = "0..*")
    private List<String> contractPaths;

    @Option(names = "--print-dafny", description = "Given a filepath, prints the Dafny code that is generated from Java")
    private Path printDafny;
    
    @Option(names = "--show-ranges", description = "Show source location ranges in diagnostics")
    private boolean showRanges;
    
    @Option(names = "--paths", description = "Show file paths instead of names")
    private boolean paths;
    
    @Option(names = "--filter-position", description = "Filter what gets verified based on a source location. The location is specified as a file path suffix, optionally followed by a colon and a line number or line range. For example, `jverify verify source1.java source2.java --filter-position=source1.dfy:5-7` will only verify things that between (and including) line 5 and 7 in the file `source1.dfy`. You can also use `:5`, `:5-`, `:-5` to specify individual lines or open ranges.")
    private String filterPositionString;
    
    @Option(names = "--include-filter-dependencies", description = "When filtering on just a file, also verify its transitive dependencies.")
    private boolean includeFilterDependencies;
    
    @Option(names = "--verifier", description = "Location of the verifier to use. Overrides environment variable JVERIFY_DAFNY or JVERIFY_STRATA, depending on the back-end used.")
    private Path customVerifier;

    @Option(names = "--verify-by-default", description = "Whether to verify code without @Verify(true). Defaults to true.", defaultValue = "true")
    private boolean verifyByDefault;

    @Option(names = "--verbose", description = "")
    private boolean verbose;
    
    @Option(names = "--track-time", description = "")
    private boolean trackTime;
    
    @Option(names = "--backend", description = "")
    private Backend backend = Backend.Dafny;

    @Override
    public Integer call() throws IOException {
        var writer = new PrintWriter(spec.commandLine().getOut());

        // In the future we'll have to add an argument to specify jar files for dependencies of the input sources,
        // And those will include the JVerify library jar.
        // But right now we manually find the JVerify library jar and include it in the compilation.
        var jverifyLibraryLocation = Path.of(URI.create(Common.getJarLocationForClass(JVerify.class)));

        File tempFile = File.createTempFile("jverify-prelude-", ".dfy");
        InputStream stream = getClass().getResourceAsStream("/additional.dfy");
        Files.copy(stream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        var dafnyPath = getBackendPath(writer);

        classPaths = classPaths == null ? List.of() : classPaths;
        List<Path> givenClassPaths = classPaths.stream()
                .flatMap(p -> Arrays.stream(p.split(File.pathSeparator)))
                .map(Path::of)
                .toList();
        List<Path> classpathEntries = Stream.concat(givenClassPaths.stream(),
                Stream.of(jverifyLibraryLocation)).toList();

        contractPaths = contractPaths == null ? List.of() : contractPaths;
        List<Path> contractPathEntries = this.contractPaths.stream().flatMap(p -> Arrays.stream(p.split(File.pathSeparator)).map(Path::of)).toList();

        var testDafnyVersion = customVerifier != null;
        var workingDirectory = Path.of(System.getProperty("user.dir"));
        var positionFilter = PositionFilter.getPositionFilter(includeFilterDependencies, filterPositionString);
        String[] additionalDafnyArguments = { 
                //"--wait-for-debugger"
        };
        var verifierOptions = new VerifierOptions(writer, workingDirectory, dafnyPath, classpathEntries,
                tempFile.toPath(), testDafnyVersion,
                printDafny, printBinaryDafny, showRanges, contractPathEntries,
                paths, additionalDafnyArguments, verifyByDefault, false, 
                positionFilter, verbose, trackTime);
        
        return verifierOptions.time("Calling Driver.verifyJavaPaths", () -> {
            try {
                return Driver.getDriver(backend, verifierOptions).verifyJavaPaths(inputs);
            } catch(IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Path getBackendPath(PrintWriter output) {
        var result = customVerifier;
        if (customVerifier == null || !Files.exists(customVerifier)) {
            var envVar = "JVERIFY_" + backend.toString();
            if (System.getenv(envVar) != null) {
                result = Path.of(System.getenv(envVar));
            }
        }
        if (result == null || !Files.exists(result)) {
            if (verbose) {
                output.println("Could not find a file at verifier path '" + result + "'");
            }
            if (backend == Backend.Dafny) {
                var isWindows = System.getProperty("os.name", "").toLowerCase().contains("windows");
                result = Path.of(isWindows ? "Dafny.exe" : "dafny");
            }
        }
        return result;
    }
}

