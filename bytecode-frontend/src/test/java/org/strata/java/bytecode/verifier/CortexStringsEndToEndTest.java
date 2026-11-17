package org.strata.java.bytecode.verifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.strata.java.bytecode.verifier.laurel.StrataBackend;
import org.strata.jverify.verifier.JVerifyResults;
import org.strata.jverify.verifier.SourceFile;

import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.strata.java.bytecode.frontend.BytecodeFrontend;
import org.strata.java.bytecode.verifier.compiler.BytecodeToCortexCompiler;
import org.strata.java.bytecode.verifier.compiler.Reporter;
import org.strata.jverify.cortex.Cmd;
import org.strata.jverify.cortex.Expr;
import org.strata.jverify.cortex.Procedure;
import org.strata.jverify.cortex.Program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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

    private static final String METHODS = """
            import org.strata.jverify.InterpretEntry;
            import static org.strata.jverify.JVerify.check;

            class Methods {
                @InterpretEntry
                static void run() {
                    String a = "hello";
                    String b = "hel";
                    check(a.length() == 5);
                    check("".length() == 0);
                    check(a.equals("hello"));          // same text, same pooled object
                    check(!a.equals(b));               // different text
                    check(!a.equals(new Object()));    // not a String
                    check(%s);
                }
            }
            """;

    /**
     * length() is the text's length; equals(Object) is true exactly for a String
     * with the same text. The receiver's static type must be String: a call on an
     * Object-typed receiver is a library call to Object.equals.
     */
    @Test
    void lengthAndEqualsFollowTheText(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "Methods.java", "Methods#run", METHODS.formatted("b.length() == 3")));
        assertFails(interpret(tmp, "Methods.java", "Methods#run", METHODS.formatted("b.equals(a)")));
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

    private static final String INTERNING = """
            import org.strata.jverify.InterpretEntry;
            import static org.strata.jverify.JVerify.check;

            class Interning {
                static String y() { return "y"; }
                @InterpretEntry
                static void run() {
                    String a = new String("x");
                    check(a != "x");                    // a new object, not the pooled one (JLS 12.5)
                    check(a.equals("x"));               // with the same text
                    check(new String().equals(""));
                    check(new String("y").intern() == "y");
                    check(new String("y").intern() == y());
                    check(a.intern() == "x");
                    check(%s);
                }
            }
            """;

    /**
     * new String(s) is a fresh object with s's text; intern() returns the pooled
     * object of the text, which for a literal is the literal's object (JLS 3.10.5).
     */
    @Test
    void newStringIsFreshAndInternReturnsThePooledObject(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "Interning.java", "Interning#run", INTERNING.formatted("a.intern() != a")));
        assertFails(interpret(tmp, "Interning.java", "Interning#run", INTERNING.formatted("a.intern() == a")));
    }

    /** A method that interns but has no string constant still runs the pool's initialization first. */
    @Test
    void internWithoutAConstantInTheMethodFindsThePool(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "Split.java", "Split#run", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Split {
                    static String canon(String s) { return s.intern(); }
                    @InterpretEntry
                    static void run() {
                        String k = new String("k");
                        check(canon(k) == "k");
                        check(canon(k) != k);
                    }
                }
                """));
    }

    private static Program compileToCortex(Path tmp, String className, String source) throws Exception {
        Path classes = Files.createTempDirectory(tmp, "classes-" + className);
        var file = new SourceFile(tmp.resolve(className + ".java"), source);
        var result = BytecodeFrontend.compile(List.of(file), List.of(VerifierDriver.specLibraryLocation()), classes,
                Writer.nullWriter());
        assertTrue(result.success(), () -> "javac failed: " + result.diagnostics());
        return new BytecodeToCortexCompiler(new Reporter(), true, List.of(VerifierDriver.specLibraryLocation()), true)
                .analyze(List.of(file.toUri()), BytecodeFrontend.readClasses(classes)).program();
    }

    /**
     * The pool only grows and a String's text never changes after construction:
     * the only writers of the pool's globals and nodes are {@code $string$init}
     * and {@code $string$intern}, and the only writers of {@code $value} outside
     * them write an object allocated by {@code new} in the same block, that is,
     * a constructor. Pins the argument for why {@code intern} is exact.
     */
    @Test
    void onlyTheInitializerAndInternWriteThePool(@TempDir Path tmp) throws Exception {
        Program p = compileToCortex(tmp, "Writers", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Writers {
                    static String f = "lit";
                    String g;
                    Writers(String s) { g = new String(s); }
                    String h(String s) { return (s + "!").intern(); }
                    @InterpretEntry
                    static void run() {
                        Writers w = new Writers("a");
                        String t = new String("b").intern();
                        check(w.g.equals("a") && t == "b" && w.h("x") == "x!");
                    }
                }
                """);
        var poolProcs = Set.of(BytecodeToCortexCompiler.STRING_INIT, BytecodeToCortexCompiler.STRING_INTERN);
        var poolGlobals = Set.of(BytecodeToCortexCompiler.STRING_POOL_HEAD, BytecodeToCortexCompiler.STRING_POOL_TAIL);
        assertTrue(p.procedures().stream().map(Procedure::name).collect(java.util.stream.Collectors.toSet())
                .containsAll(poolProcs));
        var violations = new ArrayList<String>();
        for (var proc : p.procedures()) {
            if (proc.body().isEmpty() || poolProcs.contains(proc.name())) {
                continue;
            }
            for (var block : proc.body().get().blocks()) {
                var fresh = new HashSet<String>();
                for (var cmd : block.cmds()) {
                    switch (cmd) {
                        case Cmd.New n when n.ty().equals(BytecodeToCortexCompiler.STRING) -> fresh.add(n.lhs());
                        case Cmd.New n -> fresh.remove(n.lhs());
                        case Cmd.Set st -> {
                            if (poolGlobals.contains(st.name())) {
                                violations.add(proc.name() + ": writes " + st.name());
                            }
                            if (st.value().isPresent() && st.value().get() instanceof Expr.Var v && fresh.contains(v.name())) {
                                fresh.add(st.name());
                            } else {
                                fresh.remove(st.name());
                            }
                        }
                        case Cmd.WriteField w when w.owner().equals(BytecodeToCortexCompiler.STRING_POOL) ->
                                violations.add(proc.name() + ": writes a pool node");
                        case Cmd.WriteField w when w.owner().equals(BytecodeToCortexCompiler.STRING)
                                && !(w.obj() instanceof Expr.Var v && fresh.contains(v.name())) ->
                                violations.add(proc.name() + ": writes $value of an object it did not allocate");
                        case Cmd.Call c -> fresh.removeAll(c.lhs());
                        case Cmd.VirtualCall c -> fresh.removeAll(c.lhs());
                        default -> {
                        }
                    }
                }
            }
        }
        if (!violations.isEmpty()) {
            fail(String.join("\n", violations));
        }
    }

    private static final String CONCAT = """
            import org.strata.jverify.InterpretEntry;
            import static org.strata.jverify.JVerify.check;

            class Concat {
                @InterpretEntry
                static void run() {
                    String a = "ab";
                    String b = "cd";
                    String s = a + b;
                    check(s.length() == 4);
                    check(s.equals("abcd"));
                    check(s != "abcd");                       // a new object, not the pooled literal
                    check(("x" + null).equals("xnull"));      // javac folds null into the recipe
                    boolean t = true;
                    boolean f = false;
                    check(("" + t).equals("true"));
                    check((f + "!").equals("false!"));
                    check(("<" + a + "|" + b + ">").equals("<ab|cd>"));
                    String c = a;
                    c += b;
                    check(c.equals("abcd"));
                    check(%s);
                }
            }
            """;

    /** `+` joins the operands' texts into a new String; String and boolean operands convert as the JLS says. */
    @Test
    void concatenationJoinsTextsIntoANewString(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "Concat.java", "Concat#run", CONCAT.formatted("s.equals(a + b)")));
        assertFails(interpret(tmp, "Concat.java", "Concat#run", CONCAT.formatted("s == a + b")));
    }

    /** A computed text, interned twice, yields the object the first call added to the pool. */
    @Test
    void internOfAComputedTextAddsItToThePoolOnce(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "Computed.java", "Computed#run", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Computed {
                    @InterpretEntry
                    static void run() {
                        String a = "ab";
                        String b = "cd";
                        String p = a + b;
                        String q = a + b;
                        check(p != q);
                        check(p.intern() == p);     // no pooled object had this text: p is added
                        check(q.intern() == p);     // and is what the second call finds
                        check(q.intern() != q);
                    }
                }
                """));
    }

    /** Operands with no text form in the Core yet, and other invokedynamic uses, are refused with a reason. */
    @Test
    void concatenationOfNumbersAndOtherIndyAreRefused(@TempDir Path tmp) throws Exception {
        var ran = interpret(tmp, "Refused.java", "Refused#ok", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Refused {
                    static String number(int i) { return "" + i; }
                    static Runnable lambda() { return () -> {}; }
                    @InterpretEntry
                    static void ok() { check(true); }
                }
                """);
        assertEquals(0, ran.verificationResults().verificationFailedMethods(), () -> messages(ran).toString());
        assertEquals(List.of(
                "error: not supported by the direct Cortex translation: string conversion of int in a concatenation",
                "error: not supported by the direct Cortex translation: invokedynamic run"), messages(ran));
    }
}
