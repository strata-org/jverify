package org.strata.java.bytecode.verifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.strata.jverify.verifier.JVerifyResults;
import org.strata.java.bytecode.verifier.compiler.Reporter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The driver's two input-refusal paths map to {@code NoVerdict} at exit 2.
 * Both are unreachable through the public {@code verifyJavaFiles} API -- its
 * only classfile source is the driver's own javac output, which is always
 * parseable and always verifiable -- so this exercises the package-private
 * {@link VerifierDriver#analyzeClassesDir} seam with a classes directory
 * populated by hand. Both refusals return before Strata runs, so these need
 * no {@code strata} on PATH.
 */
class VerifierDriverRefusalTest {

    private static VerifierOptions options(boolean jvmVerifyClassfiles) {
        return new VerifierOptions(
                new PrintWriter(new StringWriter(), true),
                Path.of(System.getProperty("user.dir")),
                List.of(),
                null, false, true, List.of(), false,
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
                tmp, List.of(URI.create("file:///Broken.java")), reporter, false);

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
                tmp, List.of(URI.create("file:///Broken.java")), reporter, false);

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
                tmp, List.of(URI.create("file:///Truncated.java")), reporter, false);

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
}
