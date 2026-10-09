package org.strata.java.bytecode.verifier;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.strata.java.bytecode.unfolding.Exports;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * The {@code bytecode-verifier} command line: compiles the given Java files
 * with javac, then verifies (or, with {@code --interpret}, runs) their
 * classfiles. With {@code --no-constant-folding}, javac does not fold the
 * input's constant expressions where it can avoid it.
 *
 * <p>Exit codes: 0 when every verified method verified (or the run
 * completed), 4 when an assertion failed, 2 on a usage error or when there is
 * no verdict (a front-end error), and Strata's own exit code when the backend
 * crashed.
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, new PrintWriter(System.out, true), new PrintWriter(System.err, true),
                System::getenv));
    }

    /** The command line, reading environment variables from the process environment. */
    public static CommandLine commandLine() {
        return new CommandLine(new VerifyCommand(System::getenv));
    }

    /**
     * Run the command line, writing to {@code out} and {@code err} and reading environment
     * variables through {@code getenv}; returns the exit code.
     */
    static int run(String[] args, PrintWriter out, PrintWriter err, Function<String, @Nullable String> getenv) {
        var commandLine = new CommandLine(new VerifyCommand(getenv));
        commandLine.setOut(out);
        commandLine.setErr(err);
        return commandLine.execute(args);
    }

    @Command(name = "bytecode-verifier",
            description = "Verify Java code that calls the JVerify library, through its bytecode")
    static final class VerifyCommand implements Callable<Integer> {
        private final Function<String, @Nullable String> getenv;

        VerifyCommand(Function<String, @Nullable String> getenv) {
            this.getenv = getenv;
        }

        @Spec
        CommandSpec spec;

        @Parameters(description = "Java files to verify", arity = "1..*")
        List<Path> inputs;

        @Option(names = "--classpath", description = "Includes these paths on the classpath", arity = "0..*")
        @Nullable List<String> classPaths;

        @Option(names = "--contract-path", description = "Includes contracts from source code in these paths", arity = "0..*")
        @Nullable List<String> contractPaths;

        @Option(names = "--show-ranges", description = "Has no effect: diagnostics always show their ranges")
        boolean showRanges;

        @Option(names = "--paths", description = "Show file paths instead of names")
        boolean paths;

        @Option(names = "--strata",
                description = "Path to the Strata project directory, whose StrataCLI subdirectory `lake exe strata` "
                        + "runs in. Overrides environment variable JVERIFY_STRATA. Without either, `strataLaurel` is run "
                        + "from PATH.")
        @Nullable Path strata;

        @Option(names = "--verify-by-default", description = "Whether to verify code without @Verify(true). Defaults to true.", defaultValue = "true")
        boolean verifyByDefault;

        @Option(names = "--verbose", description = "Also print Strata's own output")
        boolean verbose;

        @Option(names = "--emit-laurel", paramLabel = "FILE",
                description = "Write the serialized Laurel program to this file and exit without running verification.")
        @Nullable Path emitLaurel;

        @Option(names = "--track-time", description = "Print how long each step takes")
        boolean trackTime;

        @Option(names = "--interpret",
                description = "Concretely interpret the program instead of verifying it. Entry points are "
                        + "the methods annotated @InterpretEntry; use --entry to override and --fuel "
                        + "to bound execution.")
        boolean interpret;

        @Option(names = "--entry", paramLabel = "PROC",
                description = "Override the --interpret entry point by name, e.g. `com.example.Foo#run`, "
                        + "instead of using methods annotated @InterpretEntry. The method must be static "
                        + "and take no arguments.")
        @Nullable String entry;

        @Option(names = "--fuel", paramLabel = "N",
                description = "Maximum number of execution steps in --interpret mode (default: 10000 per call "
                        + "with --interpreter core; 100000 per entry point with --interpreter laurel).")
        @Nullable Integer fuel;

        @Option(names = "--interpreter", paramLabel = "core|laurel",
                description = "Interpreter used by --interpret: `core` (default) lowers Laurel to Core and "
                        + "runs the Strata Core interpreter; `laurel` runs the Laurel interpreter directly, "
                        + "without lowering to Core. Its --fuel bounds each entry point, not each call.")
        @Nullable String interpreter;

        @Option(names = "--no-constant-folding",
                description = "Compile the input without javac's constant folding where possible, so its operations "
                        + "on constants are verified (7 / 2 as a division, not the constant 3). Constants that decide "
                        + "a statement's control flow stay folded. Uses javac's internals, which the "
                        + "bytecode-verifier script exports.")
        boolean noConstantFolding;

        @Override
        public Integer call() throws IOException {
            var missingExports = noConstantFolding ? Exports.missing() : List.<String>of();
            if (!missingExports.isEmpty()) {
                throw new CommandLine.ParameterException(spec.commandLine(),
                        "Option '--no-constant-folding' needs the JVM options " + String.join(" ", missingExports));
            }
            // Fuel 0 would report "out of fuel" before executing anything.
            if (fuel != null && fuel <= 0) {
                throw new CommandLine.ParameterException(spec.commandLine(),
                        "Invalid value for option '--fuel': " + fuel + " (must be > 0)");
            }
            if (interpreter != null && !interpreter.equals("core") && !interpreter.equals("laurel")) {
                throw new CommandLine.ParameterException(spec.commandLine(),
                        "Invalid value for option '--interpreter': " + interpreter + " (expected core or laurel)");
            }
            if (interpreter != null && !interpret) {
                throw new CommandLine.ParameterException(spec.commandLine(),
                        "Option '--interpreter' requires '--interpret'");
            }
            for (Path input : inputs) {
                if (!Files.isRegularFile(input) || !Files.isReadable(input)) {
                    throw new CommandLine.ParameterException(spec.commandLine(), "Cannot read input file " + input);
                }
            }
            if (emitLaurel != null && Files.isDirectory(emitLaurel)) {
                throw new CommandLine.ParameterException(spec.commandLine(),
                        "Cannot write --emit-laurel file " + emitLaurel + ": it is a directory");
            }
            var strataProjectDir = emitLaurel == null ? strataProjectDir() : null;
            var out = new PrintWriter(spec.commandLine().getOut());
            var workingDirectory = Path.of(System.getProperty("user.dir"));

            // The spec library goes first, so a JVerify library on --classpath cannot shadow it:
            // compiled against :library, whose spec annotations are SOURCE-retention, the input's
            // classfiles would not carry them.
            var classPath = new ArrayList<Path>();
            classPath.add(VerifierDriver.specLibraryLocation());
            classPath.addAll(splitPaths(classPaths));

            var options = new VerifierOptions(out, workingDirectory, classPath,
                    emitLaurel, emitLaurel != null, showRanges, splitPaths(contractPaths),
                    paths, verifyByDefault, false, verbose, trackTime,
                    interpret, entry, fuel, false, interpreter, strataProjectDir, noConstantFolding);
            return new VerifierDriver(options).verifyJavaPathsExit(inputs);
        }

        /**
         * The Strata project directory to run Strata from: {@code --strata} if
         * given, else {@code JVERIFY_STRATA} if set, else none, and {@code strataLaurel}
         * runs from PATH. Either must have the {@code StrataCLI} subdirectory
         * {@code lake exe strata} runs in; one without it is a usage error,
         * reported once rather than as every run's launch failure.
         */
        private @Nullable Path strataProjectDir() {
            Path dir = strata;
            String source = "--strata";
            if (dir == null) {
                String env = getenv.apply("JVERIFY_STRATA");
                if (env == null || env.isEmpty()) {
                    return null;
                }
                dir = Path.of(env);
                source = "JVERIFY_STRATA";
            }
            if (!Files.isDirectory(dir.resolve("StrataCLI"))) {
                String hint = "StrataCLI".equals(String.valueOf(dir.getFileName()))
                        ? "; pass the Strata project directory, its parent"
                        : source.equals("JVERIFY_STRATA") ? "; unset it to run `strataLaurel` from PATH" : "";
                throw new CommandLine.ParameterException(spec.commandLine(),
                        "Not a Strata project directory (" + source + "): " + dir + " has no StrataCLI subdirectory"
                                + hint);
            }
            return dir;
        }

        private static List<Path> splitPaths(@Nullable List<String> values) {
            if (values == null) {
                return List.of();
            }
            return values.stream()
                    .flatMap(v -> Arrays.stream(v.split(File.pathSeparator)))
                    .map(Path::of)
                    .toList();
        }
    }
}
