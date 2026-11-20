package org.strata.java.bytecode.verifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.strata.java.bytecode.verifier.laurel.StrataBackend;
import org.strata.jverify.verifier.JVerifyResults;
import org.strata.jverify.verifier.SourceFile;

import javax.tools.ToolProvider;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    private static VerifierOptions options(String entry, Path... libraries) {
        var classPath = new ArrayList<Path>();
        classPath.add(VerifierDriver.specLibraryLocation());
        classPath.addAll(List.of(libraries));
        return new VerifierOptions(
                new PrintWriter(System.out, true),
                Path.of(System.getProperty("user.dir")),
                classPath,
                null, false, true, List.of(), false,
                true, /*continueOnErrors=*/true, false, false, /*interpret=*/true, entry, null, false);
    }

    private static JVerifyResults.Ran interpret(Path dir, String fileName, String entry, String source,
                                                Path... libraries) throws Exception {
        var results = run(dir, fileName, entry, source, libraries);
        return assertInstanceOf(JVerifyResults.Ran.class, results, () -> "got: " + results);
    }

    private static JVerifyResults run(Path dir, String fileName, String entry, String source, Path... libraries)
            throws Exception {
        var file = new SourceFile(dir.resolve(fileName), source);
        return new VerifierDriver(options(entry, libraries)).verifyJavaFiles(List.of(file));
    }

    private static List<String> messages(JVerifyResults results) {
        return results.diagnostics().stream().map(d -> d.getMessage(null)).toList();
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

    private static final String UNSET = """
            import org.strata.jverify.InterpretEntry;
            import static org.strata.jverify.JVerify.check;

            class Unset {
                static class Box { int v; }
                static Box box;
                static int[] cells;
                @InterpretEntry
                static void run() {
                    %s;
                    check(%s);
                }
            }
            """;

    /**
     * The translation has no null, so a static field of a class or array type
     * refers to no object until the program assigns it. Where the JVM throws
     * NullPointerException the interpreter stops, and the run has no verdict:
     * the check after the use is not reported as failed. A field of an array
     * type behaves as one of a class type does, and copying either is not a
     * use of the object.
     */
    @Test
    void aStaticFieldOfAReferenceTypeUsedBeforeItIsAssignedStopsTheRun(@TempDir Path tmp) throws Exception {
        for (String use : List.of("int n = box.v", "int n = cells.length", "int n = cells[0]", "cells[0] = 1")) {
            var results = run(tmp, "Unset.java", "Unset#run", UNSET.formatted(use, "false"));
            assertInstanceOf(JVerifyResults.DidNotRun.class, results, () -> use + ": " + results);
            assertEquals(1, messages(results).size(), () -> use + ": " + messages(results));
            assertTrue(messages(results).get(0).contains("the interpreter fails here: not a reference"),
                    () -> use + ": " + messages(results));
        }
        assertPasses(interpret(tmp, "Unset.java", "Unset#run",
                UNSET.formatted("int[] a = cells; Box b = box", "true")));
    }

    /**
     * No initializer of a class outside the program is translated, so nothing
     * assigns an array in one of its static fields: reading it is refused,
     * where reading a static field of a primitive type is not.
     */
    @Test
    void anArrayInAStaticFieldOfALibraryClassIsRefused(@TempDir Path tmp) throws Exception {
        Path librarySource = Files.createDirectories(tmp.resolve("library-source")).resolve("Codes.java");
        Files.writeString(librarySource, """
                public class Codes {
                    public static final int[] TABLE = {1, 2};
                    public static int count = 2;
                }
                """);
        Path library = Files.createDirectories(tmp.resolve("library"));
        assertEquals(0, ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-d", library.toString(), librarySource.toString()));
        var ran = interpret(Files.createDirectories(tmp.resolve("program")), "Uses.java", "Uses#ok", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Uses {
                    static int first() {
                        return Codes.TABLE[0];
                    }
                    static int count() {
                        return Codes.count;
                    }
                    @InterpretEntry
                    static void ok() {
                        check(true);
                    }
                }
                """, library);
        assertEquals(0, ran.verificationResults().verificationFailedMethods(), () -> messages(ran).toString());
        assertEquals(List.of("error: not supported by the direct Cortex translation: "
                + "arrays in static fields of library classes"), messages(ran));
    }
}
