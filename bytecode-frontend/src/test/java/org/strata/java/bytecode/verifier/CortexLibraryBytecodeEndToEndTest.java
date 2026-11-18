package org.strata.java.bytecode.verifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.strata.java.bytecode.verifier.laurel.StrataBackend;
import org.strata.jverify.verifier.JVerifyResults;
import org.strata.jverify.verifier.SourceFile;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Library methods that no contract gives a body, on the direct bytecode-to-Cortex
 * translation when interpreting: the call runs the method's own bytecode, and is
 * refused, with the reason, when that bytecode is.
 */
class CortexLibraryBytecodeEndToEndTest {

    private String wasCortex;

    @BeforeEach
    void cortex() {
        wasCortex = System.getProperty(StrataBackend.CORTEX);
        System.setProperty(StrataBackend.CORTEX, "true");
    }

    @AfterEach
    void restore() {
        if (wasCortex == null) {
            System.clearProperty(StrataBackend.CORTEX);
        } else {
            System.setProperty(StrataBackend.CORTEX, wasCortex);
        }
    }

    private static VerifierOptions options(String entry) {
        return new VerifierOptions(
                new PrintWriter(System.out, true),
                Path.of(System.getProperty("user.dir")),
                List.of(VerifierDriver.specLibraryLocation()),
                null, false, true, List.of(), false,
                true, /*continueOnErrors=*/true, false, false, /*interpret=*/true, entry, null, false);
    }

    private static JVerifyResults.Ran interpret(Path dir, String fileName, String entry, String source)
            throws Exception {
        var file = new SourceFile(dir.resolve(fileName), source);
        var results = new VerifierDriver(options(entry)).verifyJavaFiles(List.of(file));
        return assertInstanceOf(JVerifyResults.Ran.class, results, () -> "got: " + results);
    }

    private static List<String> messages(JVerifyResults.Ran ran) {
        return ran.diagnostics().stream().map(d -> d.getMessage(null)).toList();
    }

    private static void assertPasses(JVerifyResults.Ran ran) {
        assertEquals(List.of(), messages(ran));
        assertEquals(0, ran.verificationResults().verificationFailedMethods());
    }

    private static void assertFails(JVerifyResults.Ran ran) {
        assertEquals(1, ran.verificationResults().verificationFailedMethods(), () -> messages(ran).toString());
        assertTrue(messages(ran).stream().anyMatch(m -> m.contains("assertion does not hold")),
                () -> messages(ran).toString());
    }

    private static final String CALLS = """
            import org.strata.jverify.InterpretEntry;
            import static org.strata.jverify.JVerify.check;

            class Calls {
                @InterpretEntry
                static void run() {
                    Object a = new Object();
                    check(a.equals(a));
                    check(!a.equals(new Object()));
                    check(Math.abs(-5) == 5);
                    check(Math.max(3, 7) == %s);
                }
            }
            """;

    @Test
    void aLibraryMethodRunsFromItsBytecode(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "Calls.java", "Calls#run", CALLS.formatted("7")));
        assertFails(interpret(tmp, "Calls.java", "Calls#run", CALLS.formatted("3")));
    }

    @Test
    void arithmeticInALibraryMethodWrapsAsOnTheJvm(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "Wraps.java", "Wraps#run", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Wraps {
                    @InterpretEntry
                    static void run() {
                        check(Math.abs(Integer.MIN_VALUE) == Integer.MIN_VALUE);
                    }
                }
                """));
    }

    private static final String FILL = """
            import org.strata.jverify.InterpretEntry;
            import static org.strata.jverify.JVerify.check;

            class Fill {
                @InterpretEntry
                static void run() {
                    int[] a = new int[3];
                    java.util.Arrays.fill(a, 4);
                    check(a[0] + a[1] + a[2] == %s);
                }
            }
            """;

    @Test
    void aLibraryMethodWritesTheElementsOfAnArray(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "Fill.java", "Fill#run", FILL.formatted("12")));
        assertFails(interpret(tmp, "Fill.java", "Fill#run", FILL.formatted("11")));
    }

    private static final String POINT = """
            import org.strata.jverify.InterpretEntry;
            import static org.strata.jverify.JVerify.check;

            class Fields {
                @InterpretEntry
                static void run() {
                    java.awt.Point p = new java.awt.Point(3, 4);
                    p.translate(1, 2);
                    check(p.x == 4);
                    check(p.y == %s);
                }
            }
            """;

    @Test
    void aLibraryConstructorAndMethodWriteTheObjectsFields(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "Fields.java", "Fields#run", POINT.formatted("6")));
        assertFails(interpret(tmp, "Fields.java", "Fields#run", POINT.formatted("4")));
    }

    @Test
    void aRefusedOverriderNamesItsLibraryMethod(@TempDir Path tmp) throws Exception {
        var file = new SourceFile(tmp.resolve("Overrider.java"), """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Overrider {
                    @InterpretEntry
                    static void run() {
                        Object o = new java.awt.Point(1, 2);
                        check(o.equals(o));
                    }
                }
                """);
        var results = new VerifierDriver(options("Overrider#run")).verifyJavaFiles(List.of(file));
        var messages = results.diagnostics().stream().map(d -> d.getMessage(null)).toList();
        assertTrue(messages.stream().anyMatch(m -> m.contains("the library method 'java.awt.Point.equals'")),
                messages::toString);
    }

    @Test
    void aCallReachingANativeMethodIsRefused(@TempDir Path tmp) throws Exception {
        var file = new SourceFile(tmp.resolve("Native.java"), """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Native {
                    @InterpretEntry
                    static void run() {
                        Object o = new Object();
                        check(o.hashCode() == o.hashCode());
                    }
                }
                """);
        var results = new VerifierDriver(options("Native#run")).verifyJavaFiles(List.of(file));
        var refused = assertInstanceOf(JVerifyResults.NoVerdict.class, results, () -> "got: " + results);
        var messages = refused.diagnostics().stream().map(d -> d.getMessage(null)).toList();
        assertTrue(messages.stream().anyMatch(m -> m.contains(
                        "the library method 'java.lang.Object.hashCode' could not be translated")
                        && m.contains("native methods (java.lang.Object.hashCode)")),
                messages::toString);
    }
}
