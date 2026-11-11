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
 * Static fields and class initialization on the direct bytecode-to-Cortex
 * translation, run on Strata's Cortex interpreter (the only mode that route
 * supports), where the behaviour differs from the Laurel route's by design:
 * the fixtures in {@code javasupport/statics} pin the Laurel route.
 */
class CortexStaticsEndToEndTest {

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

    private static final String WRAPPED = """
            import org.strata.jverify.InterpretEntry;
            import static org.strata.jverify.JVerify.check;

            class Wrapped {
                static class Bad {
                    static int v = 7;
                    static { if (v == 7) { throw new IllegalStateException(); } }
                }
                static int r;
                @InterpretEntry
                static void handlers() {
                    try {
                        int x = Bad.v;
                    } catch (IllegalStateException e) {
                        r = 1;
                    } catch (Error e) {
                        r = 2;
                    }
                    check(r == %s);
                }
            }
            """;

    /**
     * JLS 12.4.2: an exception from a static initializer reaches the use of
     * the class as an ExceptionInInitializerError, which the handler for the
     * exception's own type does not catch.
     */
    @Test
    void anInitializerExceptionReachesTheUseWrappedInAnError(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "Wrapped.java", "Wrapped#handlers", WRAPPED.formatted("2")));
        assertFails(interpret(tmp, "Wrapped.java", "Wrapped#handlers", WRAPPED.formatted("1")));
    }

    /** A second use of a class whose initializer failed throws NoClassDefFoundError. */
    @Test
    void aFailedInitializerMakesLaterUsesThrowNoClassDefFoundError(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "Erroneous.java", "Erroneous#twice", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Erroneous {
                    static class Bad {
                        static int v = 7;
                        static { if (v == 7) { throw new IllegalStateException(); } }
                    }
                    static int r;
                    @InterpretEntry
                    static void twice() {
                        try {
                            int x = Bad.v;
                        } catch (ExceptionInInitializerError e) {
                            r = 1;
                        }
                        check(r == 1);
                        try {
                            int y = Bad.v;
                        } catch (NoClassDefFoundError e) {
                            r = 2;
                        }
                        check(r == 2);
                    }
                }
                """));
    }

    /** JLS 12.4.2, step 7: when a superclass's initializer throws, the subclass is erroneous too. */
    @Test
    void aSuperclassInitializerFailureMarksTheSubclassErroneous(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "SuperFails.java", "SuperFails#twice", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class SuperFails {
                    static class Sup {
                        static int v = 1;
                        static { if (v == 1) { throw new IllegalStateException(); } }
                    }
                    static class Sub extends Sup {
                        static int s = 3;
                    }
                    static int r;
                    @InterpretEntry
                    static void twice() {
                        try {
                            int a = Sub.s;
                        } catch (ExceptionInInitializerError e) {
                            r = 1;
                        }
                        check(r == 1);
                        try {
                            int b = Sub.s;
                        } catch (NoClassDefFoundError e) {
                            r = 2;
                        }
                        check(r == 2);
                    }
                }
                """));
    }

    /**
     * The prologue of an initializer (its fields at their defaults) runs
     * once, before the first block: with a leading loop the first block is
     * the loop header, and a prologue there would reset the counter on every
     * iteration.
     */
    @Test
    void thePrologueRunsOnceBeforeALeadingLoop(@TempDir Path tmp) throws Exception {
        assertFails(interpret(tmp, "Loop.java", "Loop#use", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Loop {
                    static class Counter {
                        static int n;
                        static {
                            while (n < 3) {
                                n++;
                            }
                            check(false);
                        }
                    }
                    @InterpretEntry
                    static void use() {
                        int x = Counter.n;
                    }
                }
                """));
    }

    /** JLS 12.4.1: a static call initializes the class that declares the method, not the class named in the call. */
    @Test
    void aStaticCallInitializesTheDeclaringClass(@TempDir Path tmp) throws Exception {
        String source = """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Inherited {
                    static int log;
                    static class Super { static int foo() { return 0; } }
                    static class Sub extends Super { static int s; static { log = 1; } }
                    @InterpretEntry
                    static void m() {
                        Sub.foo();
                        log = 2;
                        int x = Sub.s;
                        check(log == %s);
                    }
                }
                """;
        assertPasses(interpret(tmp, "Inherited.java", "Inherited#m", source.formatted("1")));
        assertFails(interpret(tmp, "Inherited.java", "Inherited#m", source.formatted("2")));
    }

    /** A method whose guard would call an initializer the translation refused is refused with that reason. */
    @Test
    void aRefusedInitializerRefusesTheMethodsThatMayRunIt(@TempDir Path tmp) throws Exception {
        var ran = interpret(tmp, "Refused.java", "Refused#ok", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Refused {
                    static int observed;
                    static class Bad {
                        static int v;
                        static { observed = 7; Object o = Bad.class; }
                    }
                    static void m() {
                        observed = 1;
                        int x = Bad.v;
                        check(observed == 1);
                    }
                    @InterpretEntry
                    static void ok() {
                        check(observed == 0);
                    }
                }
                """);
        assertEquals(0, ran.verificationResults().verificationFailedMethods(), () -> messages(ran).toString());
        assertTrue(messages(ran).stream().anyMatch(m -> m.contains(
                "the static initializer of 'Bad', which this method may run, could not be translated: ")),
                () -> messages(ran).toString());
    }

    /** A refused initializer refuses, through the initializers that run it, every method that may reach it. */
    @Test
    void aRefusedInitializerRefusesIndirectUsersToo(@TempDir Path tmp) throws Exception {
        var ran = interpret(tmp, "Transit.java", "Transit#ok", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Transit {
                    static class A {
                        static int a;
                        static { a = 5; int t = B.b; }
                    }
                    static class B {
                        static int b;
                        static { Object o = B.class; }
                    }
                    static void m() {
                        check(A.a == 0);
                    }
                    @InterpretEntry
                    static void ok() {
                        check(true);
                    }
                }
                """);
        assertEquals(0, ran.verificationResults().verificationFailedMethods(), () -> messages(ran).toString());
        // B's initializer, then A's (which runs B's), then m (which runs A's).
        assertTrue(messages(ran).stream().anyMatch(m -> m.contains(
                "the static initializer of 'B', which this method may run, could not be translated: ")),
                () -> messages(ran).toString());
        assertTrue(messages(ran).stream().anyMatch(m -> m.contains(
                "the static initializer of 'A', which this method may run, could not be translated: "
                        + "the static initializer of 'B', which this method may run")),
                () -> messages(ran).toString());
    }
}
