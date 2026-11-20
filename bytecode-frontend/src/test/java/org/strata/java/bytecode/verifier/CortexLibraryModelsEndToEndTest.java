package org.strata.java.bytecode.verifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.strata.java.bytecode.verifier.compiler.BytecodeToCortexCompiler;
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
 * The library classes, besides String, that the direct bytecode-to-Cortex
 * translation models, run on Strata's Cortex interpreter: Integer boxes, whose
 * contract's bodies run, and the classes modelled inline
 * (BytecodeToCortexCompiler.libraryMethod), which are StringBuilder, a
 * Throwable's message and cause, an enum constant's name and ordinal, and
 * records.
 */
class CortexLibraryModelsEndToEndTest {

    private static final String PREFIX = "error: not supported by the direct Cortex translation: ";
    /** How the refusal of a call to a library method that could not be translated starts; its own reason follows. */
    private static final String LIBRARY = "the library method '";

    private static final String REFERENCE_COMPARISON = "a reference comparison when an operand may be an object"
            + " that a contract body allocates: its identity may not be Java's";

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

    /** The entry runs, and the other methods are refused with the given reasons (in any order). */
    private static void assertRefused(JVerifyResults.Ran ran, String... reasons) {
        assertEquals(0, ran.verificationResults().verificationFailedMethods(), () -> messages(ran).toString());
        assertEquals(List.of(reasons).stream().map(r -> r.startsWith(LIBRARY) ? "error: " + r : PREFIX + r).sorted().toList(),
                messages(ran).stream().sorted().toList());
    }

    /** A box is an Integer object holding its value; boxing and unboxing run the contract's bodies, also javac's implicit ones. */
    @Test
    void integerBoxesHoldTheirValue(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "Boxes.java", "Boxes#run", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Boxes {
                    static Object held;
                    static int unbox(Integer b) { return b; }
                    @InterpretEntry
                    static void run() {
                        Object o = Integer.valueOf(1);
                        check(o instanceof Integer);
                        check(!(o instanceof String));
                        Integer seven = 7;
                        int x = seven;
                        check(x == 7 && seven.intValue() == 7);
                        held = 40;
                        check(unbox((Integer) held) + 2 == 42);
                    }
                }
                """));
        assertFails(interpret(tmp, "Wrong.java", "Wrong#run", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Wrong {
                    @InterpretEntry
                    static void run() {
                        Integer b = 5;
                        check(b.intValue() == 6);
                    }
                }
                """));
    }

    /**
     * Reference equality that may compare boxes is refused in a program that
     * boxes (Java's box cache decides it, and a contract body allocates each
     * box), whatever the static types that may hold a box; a program that
     * never boxes compares references freely.
     */
    @Test
    void referenceEqualityOnPossibleBoxesIsRefused(@TempDir Path tmp) throws Exception {
        assertRefused(interpret(tmp, "BoxIdentity.java", "BoxIdentity#ok", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class BoxIdentity {
                    static boolean boxes(Integer a, Integer b) { return a == b; }
                    static boolean objects(Object a, Object b) { return a != b; }
                    static boolean strings(String a, String b) { return a == b; }
                    @InterpretEntry
                    static void ok() { Integer i = 1; check(i == 1); }
                }
                """),
                REFERENCE_COMPARISON, REFERENCE_COMPARISON);
        assertPasses(interpret(tmp, "NoBoxes.java", "NoBoxes#run", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class NoBoxes {
                    static boolean objects(Object a, Object b) { return a == b; }
                    @InterpretEntry
                    static void run() { Object o = "x"; check(objects(o, o)); }
                }
                """));
    }

    /**
     * A Throwable's message and cause, set by the library constructors a
     * program exception class's {@code super(...)} reaches, are read by
     * {@code getMessage} and {@code getCause}; {@code addSuppressed}, which
     * javac calls when a resource's close throws, records nothing.
     */
    @Test
    void throwableMessageAndCause(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "Thrown.java", "Thrown#run", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Thrown {
                    static class Boom extends RuntimeException {
                        Boom(String m, Throwable cause) { super(m, cause); }
                    }
                    static class Closer implements AutoCloseable {
                        static int closed;
                        public void close() { closed++; }
                    }
                    @InterpretEntry
                    static void run() {
                        RuntimeException inner = new RuntimeException("inner");
                        int length = 0;
                        try {
                            try (Closer c = new Closer()) {
                                throw new Boom("boom", inner);
                            }
                        } catch (Boom e) {
                            length = e.getMessage().length();
                            check(e.getMessage().equals("boom"));
                            check(e.getCause() == inner);
                            check(e.getCause().getMessage().equals("inner"));
                        }
                        check(length == 4 && Closer.closed == 1);
                    }
                }
                """));
    }

    /**
     * A program class that overrides {@code getMessage} keeps every such call a dispatch, which is not modelled:
     * the call runs the JDK's {@code getMessage}, which reads a field the model replaces.
     */
    @Test
    void overriddenGetMessageIsNotInlined(@TempDir Path tmp) throws Exception {
        assertRefused(interpret(tmp, "Overrides.java", "Overrides#ok", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Overrides {
                    static class Loud extends RuntimeException {
                        Loud() { super("quiet"); }
                        @Override public String getMessage() { return "LOUD"; }
                    }
                    static String of(RuntimeException e) { return e.getMessage(); }
                    @InterpretEntry
                    static void ok() { check(new Loud().getMessage().equals("LOUD")); }
                }
                """),
                LIBRARY + "java.lang.Throwable.getMessage' could not be translated: not supported by the direct Cortex"
                        + " translation: fields of java.lang.Throwable, whose state the translation models itself");
    }

    /** A StringBuilder is a mutable text: append (returning the builder), toString and setLength(0). */
    @Test
    void stringBuilderAppendsText(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "Builder.java", "Builder#run", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Builder {
                    static StringBuilder log = new StringBuilder();
                    static void mark(boolean first) { log.append(first ? "x" : "y"); }
                    @InterpretEntry
                    static void run() {
                        StringBuilder sb = new StringBuilder();
                        sb.append("a").append(true);
                        sb.append("b");
                        check(sb.toString().equals("atrueb"));
                        check(sb.toString() != sb.toString());
                        sb.setLength(0);
                        check(sb.toString().equals(""));
                        mark(true);
                        mark(false);
                        check(log.toString().equals("xy"));
                    }
                }
                """));
        assertRefused(interpret(tmp, "BuilderRefused.java", "BuilderRefused#ok", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class BuilderRefused {
                    static void object(StringBuilder sb, Object o) { sb.append(o); }
                    static void truncate(StringBuilder sb) { sb.setLength(1); }
                    static int length(StringBuilder sb) { return sb.length(); }
                    static void parameter(StringBuilder sb, String s) { sb.append(s); }
                    @InterpretEntry
                    static void ok() { check(true); }
                }
                """),
                "string conversion of Object in StringBuilder.append",
                "StringBuilder.setLength with a length other than the constant 0",
                LIBRARY + "java.lang.StringBuilder.length' could not be translated: the library method"
                        + " 'java.lang.AbstractStringBuilder.length' could not be translated: not supported by the direct"
                        + " Cortex translation: fields of java.lang.AbstractStringBuilder, whose state the translation"
                        + " models itself",
                BytecodeToCortexCompiler.MAYBE_NULL_STRING);
    }

    /** An enum constant's name and ordinal, set by the Enum constructor every enum constructor calls. */
    @Test
    void enumNameAndOrdinal(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "Colors.java", "Colors#run", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Colors {
                    enum Color {
                        RED, GREEN { @Override String label() { return "g"; } }, BLUE;
                        String label() { return name(); }
                    }
                    @InterpretEntry
                    static void run() {
                        check(Color.GREEN.name().equals("GREEN"));
                        check(Color.RED.ordinal() == 0 && Color.BLUE.ordinal() == 2);
                        check(Color.BLUE.label().equals("BLUE") && Color.GREEN.label().equals("g"));
                        check(Color.RED.name() == "RED");
                    }
                }
                """));
    }

    /**
     * A record's constructor and accessors are ordinary methods; the
     * {@code toString}, {@code hashCode} and {@code equals} javac generates
     * are not translated, and a call to one is refused, while one the record
     * declares is translated.
     */
    @Test
    void recordsAreTranslatedExceptGeneratedMembers(@TempDir Path tmp) throws Exception {
        assertRefused(interpret(tmp, "Records.java", "Records#run", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Records {
                    record Pair(int x, String s) {}
                    record Named(String s) {
                        @Override public String toString() { return s; }
                    }
                    static String generated(Pair p) { return p.toString(); }
                    @InterpretEntry
                    static void run() {
                        Pair p = new Pair(1, "a");
                        check(p.x() == 1 && p.s().equals("a"));
                        check(new Named("n").toString().equals("n"));
                    }
                }
                """),
                "the toString javac generates for a record");
    }
}
