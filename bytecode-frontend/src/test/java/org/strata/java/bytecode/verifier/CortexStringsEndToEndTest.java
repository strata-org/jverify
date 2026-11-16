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
 * String constants on the direct bytecode-to-Cortex translation, run on
 * Strata's Cortex interpreter: a constant is a {@code java.lang.String} object
 * whose identity follows the JVM's string pool (JLS 3.10.5).
 */
class CortexStringsEndToEndTest {

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

    private static final String IDENTITY = """
            import org.strata.jverify.InterpretEntry;
            import static org.strata.jverify.JVerify.check;

            class Identity {
                static String other() { return "abc"; }
                @InterpretEntry
                static void run() {
                    String a = "abc";
                    String b = "abc";
                    String c = "xyz";
                    check(a == b);
                    check(a == other());
                    check(%s);
                }
            }
            """;

    /** Equal constants are one object, in every method; different constants are different objects. */
    @Test
    void equalConstantsAreOneObjectAndDifferentOnesAreNot(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "Identity.java", "Identity#run", IDENTITY.formatted("a != c")));
        assertFails(interpret(tmp, "Identity.java", "Identity#run", IDENTITY.formatted("a == c")));
    }

    /** A constant held as an Object is a String object: instanceof sees it, and a cast to another class throws. */
    @Test
    void aConstantIsAStringObject(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "AsObject.java", "AsObject#run", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class AsObject {
                    @InterpretEntry
                    static void run() {
                        Object o = "hi";
                        check(o instanceof String);
                        boolean caught = false;
                        try {
                            Integer i = (Integer) o;
                        } catch (ClassCastException e) {
                            caught = true;
                        }
                        check(caught);
                    }
                }
                """));
    }

    /** A constant used inside a loop body and in a static initializer reads the one pooled object. */
    @Test
    void theOneObjectIsReadInLoopsAndInitializers(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "Pool.java", "Pool#run", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Pool {
                    static String s = "k";
                    @InterpretEntry
                    static void run() {
                        int same = 0;
                        for (int i = 0; i < 3; i++) {
                            if ("k" == s) { same++; }
                        }
                        check(same == 3);
                    }
                }
                """));
    }
}
