package org.strata.java.bytecode.verifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.strata.jverify.verifier.JVerifyResults;
import org.strata.jverify.verifier.SourceFile;
import org.strata.java.bytecode.verifier.compiler.LibraryContracts;
import org.strata.java.bytecode.verifier.compiler.Reporter;
import org.strata.java.bytecode.verifier.laurel.StrataDiagnostic;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The driver's input-refusal paths map to {@code NoVerdict} at exit 2. The
 * two classfile refusals are unreachable through the public
 * {@code verifyJavaFiles} API -- its only classfile source is the driver's
 * own javac output, which is always parseable and always verifiable -- so
 * they exercise the package-private {@link VerifierDriver#analyzeClassesDir}
 * seam with a classes directory populated by hand. A missing or unreadable
 * {@code contractSourcePath} entry is refused through {@code verifyJavaFiles}.
 * Every refusal returns before Strata runs, so these need no {@code strata}
 * on PATH.
 */
class VerifierDriverRefusalTest {

    private static VerifierOptions options(boolean jvmVerifyClassfiles) {
        return options(jvmVerifyClassfiles, List.of());
    }

    private static VerifierOptions options(boolean jvmVerifyClassfiles, List<Path> contractSourcePath) {
        return new VerifierOptions(
                new PrintWriter(new StringWriter(), true),
                Path.of(System.getProperty("user.dir")),
                List.of(),
                null, false, true, contractSourcePath, false,
                true, false, false, false, false, null, null, jvmVerifyClassfiles);
    }

    /** int method whose body is a bare void return -> one VerifyError. */
    private static void writeUnverifiableClass(Path tmp) throws Exception {
        Files.write(tmp.resolve("Broken.class"),
                ClassFile.of().build(ClassDesc.of("Broken"), cb -> cb
                        .withMethodBody("bad", MethodTypeDesc.ofDescriptor("()I"),
                                ClassFile.ACC_STATIC, code -> code.return_())));
    }

    @Test
    void unverifiableInputMapsToNoVerdictExit2(@TempDir Path tmp) throws Exception {
        writeUnverifiableClass(tmp);

        var reporter = new Reporter();
        var results = new VerifierDriver(options(true)).analyzeClassesDir(
                tmp, List.of(URI.create("file:///Broken.java")), reporter, false, LibraryContracts.none());

        var noVerdict = assertInstanceOf(JVerifyResults.NoVerdict.class, results);
        assertEquals(2, noVerdict.exitCode());
        assertTrue(results.diagnostics().stream()
                        .anyMatch(d -> d.getMessage(null).contains("input failed JVM bytecode verification")),
                () -> "gate refusal should reach the verdict: " + results.diagnostics());
    }

    /**
     * With {@code jvmVerifyClassfiles} off, the gate does not run: the same
     * unverifiable class gets past it and is refused only by the input-tier
     * check (the handcrafted class carries no debug information).
     */
    @Test
    void gateIsSkippedWhenJvmVerifyClassfilesIsOff(@TempDir Path tmp) throws Exception {
        writeUnverifiableClass(tmp);

        var reporter = new Reporter();
        var results = new VerifierDriver(options(false)).analyzeClassesDir(
                tmp, List.of(URI.create("file:///Broken.java")), reporter, false, LibraryContracts.none());

        assertTrue(results.diagnostics().stream()
                        .noneMatch(d -> d.getMessage(null).contains("JVM bytecode verification")),
                () -> "the gate must not run when disabled: " + results.diagnostics());
        assertTrue(results.diagnostics().stream()
                        .anyMatch(d -> d.getMessage(null).contains("no debug information")),
                () -> "input should fall through to the input-tier check: " + results.diagnostics());
    }

    @Test
    void unparseableInputMapsToNoVerdictExit2(@TempDir Path tmp) throws Exception {
        // A valid class truncated to half its bytes -> structurally unparseable.
        byte[] valid = ClassFile.of().build(ClassDesc.of("Truncated"), cb -> cb
                .withMethodBody("m", MethodTypeDesc.ofDescriptor("()I"),
                        ClassFile.ACC_STATIC, code -> code.iconst_0().ireturn()));
        Files.write(tmp.resolve("Truncated.class"), java.util.Arrays.copyOf(valid, valid.length / 2));

        var reporter = new Reporter();
        var results = new VerifierDriver(options(false)).analyzeClassesDir(
                tmp, List.of(URI.create("file:///Truncated.java")), reporter, false, LibraryContracts.none());

        var noVerdict = assertInstanceOf(JVerifyResults.NoVerdict.class, results);
        assertEquals(2, noVerdict.exitCode());
        // Reported verbatim -- no misleading "JVM bytecode verification" prefix
        // on a structural read failure.
        assertTrue(results.diagnostics().stream()
                        .anyMatch(d -> d.getMessage(null).contains("Truncated.class is not parseable")),
                () -> "parse refusal should reach the verdict: " + results.diagnostics());
        assertTrue(results.diagnostics().stream()
                        .noneMatch(d -> d.getMessage(null).contains("JVM bytecode verification")),
                () -> "a parse failure must not be labelled a verification failure: " + results.diagnostics());
    }

    private static SourceFile okFile(Path tmp) {
        return new SourceFile(tmp.resolve("Ok.java"), "public class Ok { static int one() { return 1; } }");
    }

    /**
     * Verify {@code file} with {@code contractSourcePath}: NoVerdict at exit 2
     * with exactly one error, at {@code file}, whose message contains {@code expected}.
     */
    private static void assertRefusedWithOneError(SourceFile file, List<Path> contractSourcePath,
                                                  String expected) throws Exception {
        var results = new VerifierDriver(options(false, contractSourcePath)).verifyJavaFiles(List.of(file));

        var noVerdict = assertInstanceOf(JVerifyResults.NoVerdict.class, results);
        assertEquals(2, noVerdict.exitCode());
        assertEquals(1, results.diagnostics().size(), () -> "one refusal expected: " + results.diagnostics());
        var d = assertInstanceOf(StrataDiagnostic.class, results.diagnostics().getFirst());
        assertEquals(file.toUri(), d.getUri());
        assertEquals(javax.tools.Diagnostic.Kind.ERROR, d.getKind());
        assertTrue(d.getMessage(null).contains(expected),
                () -> "expected '" + expected + "' in: " + d.getMessage(null));
    }

    /**
     * A {@code contractSourcePath} entry that does not exist ends the run
     * after javac with one error at the first input and no verdict.
     */
    @Test
    void missingContractSourcePathMapsToNoVerdictExit2(@TempDir Path tmp) throws Exception {
        Path missing = tmp.resolve("no-such-contracts");
        assertRefusedWithOneError(okFile(tmp), List.of(missing), "Could not find path: " + missing);
    }

    /** A {@code .jar} entry that is not a zip file is refused the same way. */
    @Test
    void corruptContractJarMapsToNoVerdictExit2(@TempDir Path tmp) throws Exception {
        Path jar = tmp.resolve("contracts.jar");
        Files.writeString(jar, "not a zip file");
        assertRefusedWithOneError(okFile(tmp), List.of(jar), "Could not read contract source " + jar + ": ");
    }

    /** A directory whose name ends in {@code .jar} is refused, not walked. */
    @Test
    void directoryNamedJarMapsToNoVerdictExit2(@TempDir Path tmp) throws Exception {
        Path dir = Files.createDirectory(tmp.resolve("contracts.jar"));
        assertRefusedWithOneError(okFile(tmp), List.of(dir),
                "Could not read contract source " + dir + ": a directory, not a jar");
    }

    /** A contract source that is not UTF-8 is refused, naming the file. */
    @Test
    void nonUtf8ContractSourceMapsToNoVerdictExit2(@TempDir Path tmp) throws Exception {
        Path dir = tmp.resolve("contracts");
        Path source = Files.createDirectories(dir.resolve("q")).resolve("Latin1.java");
        Files.write(source,
                "package q; class Latin1 { /* caf\u00e9 */ }".getBytes(StandardCharsets.ISO_8859_1));
        assertRefusedWithOneError(okFile(tmp), List.of(dir),
                "Could not read contract source " + source + ": not valid UTF-8");
    }

    /**
     * A jar whose central directory lists a {@code .java} entry that cannot be
     * read (its local header is corrupt) passes source collection; the read
     * fails while the contracts load, and is refused the same way.
     */
    @Test
    void unreadableContractJarEntryMapsToNoVerdictExit2(@TempDir Path tmp) throws Exception {
        Path jar = tmp.resolve("contracts.jar");
        try (var out = new ZipOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new ZipEntry("q/C.java"));
            out.write("package q; class C { }".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        byte[] bytes = Files.readAllBytes(jar);
        bytes[0] = 'X';   // the first local file header's signature ("PK\3\4")
        Files.write(jar, bytes);
        assertRefusedWithOneError(okFile(tmp), List.of(jar),
                "Could not read contract source jar:" + jar.toUri() + "!/q/C.java: ");
    }
}
