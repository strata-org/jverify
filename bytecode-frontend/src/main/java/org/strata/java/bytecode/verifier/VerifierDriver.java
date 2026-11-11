package org.strata.java.bytecode.verifier;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.strata.jverify.common.Position;
import org.strata.jverify.common.Range;
import org.strata.jverify.verifier.DiagnosticWithRange;
import org.strata.jverify.verifier.JVerifyResults;
import org.strata.java.bytecode.frontend.BytecodeFrontend;
import org.strata.java.bytecode.unfolding.ConstantUnfolding;
import org.strata.java.bytecode.verifier.compiler.BytecodeToLaurelCompiler;
import org.strata.java.bytecode.verifier.compiler.LibraryContracts;
import org.strata.java.bytecode.verifier.compiler.Reporter;
import org.strata.java.bytecode.verifier.laurel.StrataBackend;
import org.strata.java.bytecode.verifier.laurel.StrataDiagnostic;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * End-to-end verification driver: javac ({@code -g -Xjcov}) -> classfiles ->
 * direct bytecode-to-Laurel translation -> strataLaurel.
 */
public class VerifierDriver {

    private final VerifierOptions options;

    public VerifierDriver(VerifierOptions options) {
        this.options = options;
    }

    private static @Nullable Path specLibrary;

    /**
     * The JVerify spec library ({@code org.strata.jverify}) the inputs are
     * compiled against: a CLASS-retention build of {@code :library}, so the
     * spec annotations survive into the classfiles this frontend reads. This
     * module's jar carries it as a resource (see build.gradle.kts); it is
     * extracted once, and again if the file is gone, as a temp-directory
     * cleaner can make it. {@code :library} itself keeps SOURCE retention, so
     * the annotations do not end up in the classfiles of user builds.
     */
    public static synchronized Path specLibraryLocation() {
        if (specLibrary == null || !Files.exists(specLibrary)) {
            try {
                Path jar = Files.createTempFile("bytecode-frontend-lib", ".jar");
                jar.toFile().deleteOnExit();
                try (InputStream in = VerifierDriver.class.getResourceAsStream("bytecode-frontend-lib.jar");
                     var out = Files.newOutputStream(jar)) {
                    Objects.requireNonNull(in, "bytecode-frontend-lib.jar resource missing").transferTo(out);
                }
                specLibrary = jar;
            } catch (IOException e) {
                throw new UncheckedIOException("cannot extract the spec library", e);
            }
        }
        return specLibrary;
    }

    public JVerifyResults verifyJavaPaths(List<Path> files) throws IOException {
        return verifyJavaFiles(readSources(files));
    }

    /** Verify {@code files}, print the diagnostics and summary, and return the exit code. */
    public int verifyJavaPathsExit(List<Path> files) throws IOException {
        return verifyJavaFilesExit(readSources(files));
    }

    private List<JavaFileObject> readSources(List<Path> files) throws IOException {
        List<JavaFileObject> readFiles = new ArrayList<>();
        for (Path p : files) {
            Path normalized = options.workingDirectory().resolve(p).normalize();
            // Read when javac asks, so that a file it cannot decode is a Java error, not an exception.
            readFiles.add(new SimpleJavaFileObject(normalized.toUri(), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
                    try {
                        return Files.readString(normalized);
                    } catch (CharacterCodingException e) {
                        throw new IOException("not valid UTF-8", e);
                    }
                }
            });
        }
        return readFiles;
    }

    public JVerifyResults verifyJavaFiles(List<JavaFileObject> readFiles) throws IOException {
        var reporter = new Reporter();

        // 1. javac with -g -Xjcov. A Java error stops the run with usage exit
        //    (NoVerdict).
        Path classesDir = Files.createTempDirectory("bytecode-classes");
        try {
            var compileResult = options.time("Compiling Java with -g -Xjcov",
                    () -> {
                        try {
                            return BytecodeFrontend.compile(readFiles,
                                    List.copyOf(options.classPathEntries()), classesDir, options.outWriter(),
                                    options.noConstantFolding() ? ConstantUnfolding::install : null);
                        } catch (IOException e) {
                            if (e instanceof org.strata.java.bytecode.frontend.SuperclassCycleException) {
                                reporter.reportError(readFiles.getFirst().toUri(), Reporter.NO_RANGE, e.getMessage());
                                return null;
                            }
                            throw new RuntimeException(e);
                        }
                    });
            if (compileResult == null) {
                return new JVerifyResults.NoVerdict(new ArrayList<>(reporter.diagnostics()), 2);
            }
            if (!compileResult.success()) {
                var diagnostics = new ArrayList<Diagnostic<?>>();
                for (var d : compileResult.diagnostics()) {
                    if (d.getKind() == Diagnostic.Kind.ERROR) {
                        diagnostics.add(javacDiagnostic(d));
                    }
                }
                return new JVerifyResults.NoVerdict(diagnostics, 2 /* usage */);
            }

            // 2. The rest of the pipeline (read the classfiles, translate to
            //    Laurel, run Strata) lives in analyzeClassesDir. The frontend
            //    gets the input file URIs (paths only, NO source text): a
            //    class is matched to its URI by the SourceFile attribute
            //    name, and all positions come from the bytecode.
            List<URI> sourceUris = new ArrayList<>();
            for (var file : readFiles) {
                sourceUris.add(file.toUri());
            }

            // 3. Library contracts: object-contract.java is always available
            //    (compiled on first use); the contractSourcePath sources come
            //    on top. A missing or unreadable contractSourcePath entry stops
            //    the run (a jar entry that fails to read later, while the
            //    contracts load, stops it the same way, in analyzeClassesDir).
            List<JavaFileObject> userSources;
            try {
                userSources = LibraryContracts.collectUserSources(options.workingDirectory(),
                        options.contractSourcePath());
            } catch (IllegalArgumentException | UncheckedIOException e) {
                URI anchor = sourceUris.isEmpty() ? null : sourceUris.getFirst();
                reporter.reportError(anchor, Reporter.NO_RANGE, e.getMessage());
                return new JVerifyResults.NoVerdict(new ArrayList<>(reporter.diagnostics()), 2);
            }
            var contracts = LibraryContracts.of(LibraryContracts.objectContractSource(), userSources,
                    List.copyOf(options.classPathEntries()));
            return analyzeClassesDir(classesDir, sourceUris, reporter,
                    compileResult.degradedToLineNumbers(), contracts, compileResult.types());
        } finally {
            deleteRecursively(classesDir);
        }
    }

    /**
     * The post-compile pipeline: read the classfiles under {@code classesDir},
     * translate to Laurel, and run Strata. Package-private as the testable
     * seam for the two input-refusal paths -- structurally unparseable input,
     * and the JVM bytecode-verification gate (when
     * {@link VerifierOptions#jvmVerifyClassfiles} is set) -- both unreachable
     * through the public API, whose only classfile source is this driver's
     * own always-verifiable javac output.
     *
     * @param tierIsSelfInflicted true when this run's own javac step degraded
     *        to plain {@code -g} (see {@link BytecodeFrontend.CompileResult#degradedToLineNumbers})
     * @param contracts the library contracts the translation binds and applies
     * @param javacTypes what javac knew of the classes the classfiles mention
     *        ({@link BytecodeFrontend.CompileResult#types})
     */
    JVerifyResults analyzeClassesDir(Path classesDir, List<URI> sourceUris, Reporter reporter,
                                     boolean tierIsSelfInflicted, LibraryContracts contracts,
                                     Map<String, org.strata.java.bytecode.frontend.TypeFacts> javacTypes)
            throws IOException {
        List<BytecodeFrontend.ClassSource> classes;
        try {
            classes = options.time("Reading classfiles", () -> {
                try {
                    return BytecodeFrontend.readClasses(classesDir);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (org.strata.java.bytecode.frontend.UnparseableClassfileException e) {
            // Structurally unreadable input (truncated/corrupt bytes): a
            // read-level refusal, distinct from the content-level
            // verification gate. The exception message already names the file
            // and the parser's reason, so report it verbatim.
            URI anchor = sourceUris.isEmpty() ? null : sourceUris.getFirst();
            reporter.reportError(anchor, Reporter.NO_RANGE, e.getMessage());
            return new JVerifyResults.NoVerdict(new ArrayList<>(reporter.diagnostics()), 2);
        }
        if (Boolean.getBoolean(StrataBackend.CORTEX)) {
            if (!options.interpret()) {
                options.outWriter().println(StrataBackend.CORTEX_INTERPRET_ONLY);
                return new JVerifyResults.DidNotRun(new ArrayList<>(reporter.diagnostics()), -1, -1);
            }
            return analyzeOnCortex(classes, sourceUris, reporter, javacTypes);
        }
        org.strata.java.bytecode.verifier.compiler.BytecodeToLaurelCompiler.AnalysisResult analysis;
        try {
            analysis = options.time("Compiling bytecode to Laurel",
                    () -> new BytecodeToLaurelCompiler(reporter, options.verifyByDefault(),
                            List.copyOf(options.classPathEntries()), tierIsSelfInflicted,
                            options.jvmVerifyClassfiles(), contracts)
                            .withJavacTypes(javacTypes)
                            .withAllowUncheckedExceptionsByDefault(options.allowUncheckedExceptions())
                            .withCastImplementations(options.interpret())
                            .analyze(sourceUris, classes));
        } catch (org.strata.java.bytecode.verifier.compiler.RefusedInputException e) {
            // The input was refused before translation -- either it carries
            // no debug information (input-tier gate) or it failed JVM
            // bytecode verification -- or the contract sources could not be
            // loaded. Either way the reason is already on the reporter.
            return new JVerifyResults.NoVerdict(new ArrayList<>(reporter.diagnostics()), 2);
        }

        var diagnostics = new ArrayList<>(reporter.diagnostics());
        if (reporter.hasErrors() && !options.continueOnErrors()) {
            return new JVerifyResults.NoVerdict(diagnostics, 2);
        }

        // Serialize + run Strata.
        var results = new StrataBackend(options)
                .withStaticEffectNotes(analysis.staticNotes())
                .verifyProgram(analysis.program(), analysis.positions(), analysis.methodStatuses());
        var allDiagnostics = new ArrayList<Diagnostic<?>>(diagnostics);
        allDiagnostics.addAll(results.diagnostics());
        return switch (results) {
            case JVerifyResults.Ran r -> new JVerifyResults.Ran(allDiagnostics, r.exitCode(), r.verificationResults());
            case JVerifyResults.DidNotRun d -> new JVerifyResults.DidNotRun(allDiagnostics, d.exitCode(), d.strataExitCode());
            case JVerifyResults.NoVerdict n -> new JVerifyResults.NoVerdict(allDiagnostics, n.exitCode());
        };
    }

    private JVerifyResults analyzeOnCortex(List<BytecodeFrontend.ClassSource> classes, List<URI> sourceUris,
                                           Reporter reporter,
                                           Map<String, org.strata.java.bytecode.frontend.TypeFacts> javacTypes) {
        var analysis = options.time("Compiling bytecode to Cortex",
                () -> new org.strata.java.bytecode.verifier.compiler.BytecodeToCortexCompiler(reporter,
                        options.verifyByDefault(), List.copyOf(options.classPathEntries()),
                        options.interpret())
                        .withJavacTypes(javacTypes)
                        .analyze(sourceUris, classes));
        var diagnostics = new ArrayList<Diagnostic<?>>(reporter.diagnostics());
        if (reporter.hasErrors() && !options.continueOnErrors()) {
            return new JVerifyResults.NoVerdict(diagnostics, 2);
        }
        var results = new StrataBackend(options)
                .withStaticEffectNotes(analysis.staticNotes())
                .verifyCortexProgram(analysis.program(), analysis.positions(), analysis.methodStatuses());
        diagnostics.addAll(results.diagnostics());
        return switch (results) {
            case JVerifyResults.Ran r -> new JVerifyResults.Ran(diagnostics, r.exitCode(), r.verificationResults());
            case JVerifyResults.DidNotRun d -> new JVerifyResults.DidNotRun(diagnostics, d.exitCode(), d.strataExitCode());
            case JVerifyResults.NoVerdict n -> new JVerifyResults.NoVerdict(diagnostics, n.exitCode());
        };
    }

    public int verifyJavaFilesExit(List<JavaFileObject> readFiles) throws IOException {
        return report(verifyJavaFiles(readFiles));
    }

    private int report(JVerifyResults results) {
        outputVerificationResults(results, options.outWriter());
        options.outWriter().flush();
        return results.exitCode();
    }

    /** Map a javac diagnostic into our range-carrying diagnostic. */
    private static Diagnostic<?> javacDiagnostic(Diagnostic<? extends JavaFileObject> d) {
        URI uri = d.getSource() != null ? d.getSource().toUri() : URI.create("file:///unknown");
        var start = new Position((int) Math.max(1, d.getLineNumber()), (int) Math.max(1, d.getColumnNumber()));
        var range = new Range(start, new Position(start.line(), start.character() + 1));
        return new Reporter.FrontendDiagnostic(uri, range,
                "Java compilation had an error: " + d.getMessage(null),
                StrataDiagnostic.SEVERITY_ERROR);
    }

    /** Print the diagnostics, the error count and the per-method counts. */
    private void outputVerificationResults(JVerifyResults results, Writer outputWriter) {
        var pw = new PrintWriter(outputWriter);
        for (var diagnostic : results.diagnostics()) {
            pw.println(formatDiagnostic(options.showFilepaths(), diagnostic));
        }
        if (options.emitLaurelOnly() && results.exitCode() == 0) {
            pw.println("Wrote the Laurel program to " + options.printSerializedOutputProgram()
                    + " without verifying it.");
            return;
        }
        var verificationResults = results.verdictOrNull();
        int errorCount = (int) results.diagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR).count();
        // Soundness backstop: a non-zero exit code must never read as "0 errors".
        if (errorCount == 0 && results.exitCode() != 0) {
            pw.println("Verification did not complete (exit code "
                    + results.exitCode() + "); reporting as failed.");
            errorCount = 1;
        }
        pw.println(String.format("Found %s errors", errorCount));
        if (verificationResults != null) {
            pw.println(String.format("Verified methods: %s", verificationResults.verificationPassedMethods()));
            pw.println(String.format("Failed methods: %s", verificationResults.verificationFailedMethods()));
            pw.println(String.format("Skipped methods: %s", verificationResults.verificationSkippedMethods()));
        }
    }

    public static String formatMessage(Diagnostic<?> diagnostic) {
        return diagnostic.getMessage(null);
    }

    private static String formatDiagnostic(boolean filePath, Diagnostic<?> diagnostic) {
        if (diagnostic instanceof DiagnosticWithRange withRange) {
            var filePart = filePath ? withRange.filePath() : withRange.filename();
            return filePart + "(" + withRange.getRange() + "): " + formatMessage(diagnostic);
        }
        return diagnostic.getMessage(null);
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            for (Path p : stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).collect(Collectors.toList())) {
                Files.deleteIfExists(p);
            }
        }
    }
}
