package org.strata.java.bytecode.verifier;

import org.strata.jverify.common.Position;
import org.strata.jverify.common.Range;
import org.strata.jverify.verifier.DiagnosticWithRange;
import org.strata.jverify.verifier.JVerifyResults;
import org.strata.jverify.verifier.SourceFile;
import org.strata.java.bytecode.frontend.BytecodeFrontend;
import org.strata.java.bytecode.verifier.compiler.BytecodeToLaurelCompiler;
import org.strata.java.bytecode.verifier.compiler.Reporter;
import org.strata.java.bytecode.verifier.laurel.StrataBackend;
import org.strata.java.bytecode.verifier.laurel.StrataDiagnostic;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * End-to-end verification driver: javac ({@code -g -Xjcov}) -> classfiles ->
 * Decompiler -> Laurel -> StrataCLI. The bytecode-frontend counterpart of
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
            readFiles.add(new SourceFile(normalized, Files.readString(normalized)));
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
                                    List.copyOf(options.classPathEntries()), classesDir,
                                    options.outWriter());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            if (!compileResult.success()) {
                var diagnostics = new ArrayList<Diagnostic<?>>();
                for (var d : compileResult.diagnostics()) {
                    if (d.getKind() == Diagnostic.Kind.ERROR) {
                        diagnostics.add(javacDiagnostic(d));
                    }
                }
                return new JVerifyResults.NoVerdict(diagnostics, 2 /* picocli USAGE */);
            }

            // 2. Decompile + compile to Laurel.
            Map<URI, String> sources = new LinkedHashMap<>();
            for (var file : readFiles) {
                sources.put(file.toUri(), file.getCharContent(false).toString());
            }
            var classes = options.time("Decompiling classfiles", () -> {
                try {
                    return BytecodeFrontend.decompile(classesDir);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            var analysis = options.time("Compiling bytecode to Laurel",
                    () -> new BytecodeToLaurelCompiler(reporter, options.verifyByDefault(),
                            List.copyOf(options.classPathEntries()))
                            .analyze(sources, classes));

            var diagnostics = new ArrayList<>(reporter.diagnostics());
            if (reporter.hasErrors() && !options.continueOnErrors()) {
                return new JVerifyResults.NoVerdict(diagnostics, 2);
            }

            // 3. Serialize + run Strata.
            var results = new StrataBackend(options)
                    .verifyProgram(analysis.program(), analysis.filesMap(), analysis.methodStatuses());
            var allDiagnostics = new ArrayList<Diagnostic<?>>(diagnostics);
            allDiagnostics.addAll(results.diagnostics());
            return switch (results) {
                case JVerifyResults.Ran r -> new JVerifyResults.Ran(allDiagnostics, r.exitCode(), r.verificationResults());
                case JVerifyResults.DidNotRun d -> new JVerifyResults.DidNotRun(allDiagnostics, d.exitCode());
                case JVerifyResults.NoVerdict n -> new JVerifyResults.NoVerdict(allDiagnostics, n.exitCode());
            };
        } finally {
            deleteRecursively(classesDir);
        }
    }

    public int verifyJavaFilesExit(List<JavaFileObject> readFiles) throws IOException {
        var results = verifyJavaFiles(readFiles);
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
        int errorCount = verificationResults != null
                ? verificationResults.verificationFailedAssertions()
                : results.diagnostics().size();
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
