package org.strata.java.bytecode.verifier;

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
import java.util.stream.Collectors;

/**
 * End-to-end verification driver: javac ({@code -g -Xjcov}) -> classfiles ->
 * direct bytecode-to-Laurel translation -> StrataCLI. The bytecode-frontend counterpart of
 * JFE's {@code Driver.getDriver(options).verifyJavaFiles(files)}.
 */
public class VerifierDriver {

    private final VerifierOptions options;

    public VerifierDriver(VerifierOptions options) {
        this.options = options;
    }

    public JVerifyResults verifyJavaPaths(List<Path> files) throws IOException {
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
        return verifyJavaFiles(readFiles);
    }

    public JVerifyResults verifyJavaFiles(List<JavaFileObject> readFiles) throws IOException {
        var reporter = new Reporter();

        // 1. javac with -g -Xjcov. A Java error stops the run with usage exit
        //    (mirrors JFE's NoVerdict on javac errors).
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
                return new JVerifyResults.NoVerdict(diagnostics, 2 /* picocli USAGE */);
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
        org.strata.java.bytecode.verifier.compiler.BytecodeToLaurelCompiler.AnalysisResult analysis;
        try {
            analysis = options.time("Compiling bytecode to Laurel",
                    () -> new BytecodeToLaurelCompiler(reporter, options.verifyByDefault(),
                            List.copyOf(options.classPathEntries()), tierIsSelfInflicted,
                            options.jvmVerifyClassfiles(), contracts)
                            .withJavacTypes(javacTypes)
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
                .verifyProgram(analysis.program(), analysis.positions(), analysis.methodStatuses());
        var allDiagnostics = new ArrayList<Diagnostic<?>>(diagnostics);
        allDiagnostics.addAll(results.diagnostics());
        return switch (results) {
            case JVerifyResults.Ran r -> new JVerifyResults.Ran(allDiagnostics, r.exitCode(), r.verificationResults());
            case JVerifyResults.DidNotRun d -> new JVerifyResults.DidNotRun(allDiagnostics, d.exitCode());
            case JVerifyResults.NoVerdict n -> new JVerifyResults.NoVerdict(allDiagnostics, n.exitCode());
        };
    }

    public int verifyJavaFilesExit(List<JavaFileObject> readFiles) throws IOException {
        return report(verifyJavaFiles(readFiles));
    }

    /** {@link #verifyJavaPaths}, reporting the results as JFE's CLI does; returns the exit code. */
    public int verifyJavaPathsExit(List<Path> files) throws IOException {
        return report(verifyJavaPaths(files));
    }

    private int report(JVerifyResults results) {
        outputVerificationResults(results, options.outWriter());
        options.outWriter().flush();
        return results.exitCode();
    }

    /** Map a javac diagnostic into our range-carrying diagnostic (message: JFE's javaError key). */
    private static Diagnostic<?> javacDiagnostic(Diagnostic<? extends JavaFileObject> d) {
        URI uri = d.getSource() != null ? d.getSource().toUri() : URI.create("file:///unknown");
        var start = new Position((int) Math.max(1, d.getLineNumber()), (int) Math.max(1, d.getColumnNumber()));
        var range = new Range(start, new Position(start.line(), start.character() + 1));
        return new Reporter.FrontendDiagnostic(uri, range,
                "Java compilation had an error: " + d.getMessage(null),
                StrataDiagnostic.SEVERITY_ERROR);
    }

    /** Port of JFE Driver.outputVerificationResults (non-javac branch). */
    private void outputVerificationResults(JVerifyResults results, Writer outputWriter) {
        var pw = new PrintWriter(outputWriter);
        for (var diagnostic : results.diagnostics()) {
            pw.println(formatDiagnostic(options.showFilepaths(), diagnostic));
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
