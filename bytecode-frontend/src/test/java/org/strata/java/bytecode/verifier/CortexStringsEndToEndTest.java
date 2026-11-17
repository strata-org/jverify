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

    /**
     * A program that interns but has no string constant at all still gets the
     * pool: the guard runs {@code $string$init}, which then holds only the
     * pool's sentinel, in the prologue of the method that interns.
     */
    @Test
    void internWithoutAnyConstantFindsThePool(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "NoConstant.java", "NoConstant#run", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class NoConstant {
                    @InterpretEntry
                    static void run() {
                        String e = new String();
                        check(e.intern() == e);
                        check(new String().intern() == e);
                    }
                }
                """));
    }

    /**
     * Constants resolve lazily (JVMS 5.1): a string interned before the first
     * {@code ldc} of a constant with its text is the object that constant
     * becomes, in every method; a constant resolved first keeps its object.
     */
    @Test
    void aConstantResolvesToAStringInternedBeforeItsFirstUse(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "Lazy.java", "Lazy#run", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Lazy {
                    static String lit() { return "qzxw1"; }
                    @InterpretEntry
                    static void run() {
                        String a = "qz";
                        String b = "xw1";
                        String p = a + b;
                        check(p.intern() == p);          // nothing with this text is in the pool: p joins it
                        check(lit() == p);               // the constant, first resolved now, is p
                        check(lit() == p);               // and stays p
                        String q = new String("qz");
                        check(q.intern() == a);          // "qz" was resolved before: it is the pooled one
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
                    String h(String s) { String t = new String(s); return (t + "!").intern(); }
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
            violations.addAll(poolWritesOutsideConstructors(proc, poolGlobals));
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
                    String n = new String("q");
                    check((n + c + n).equals("qabcdq"));  // allocated here, assigned from a concatenation
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
                    static String field;
                    static String number(int i) { return "" + i; }
                    static Runnable lambda() { return () -> {}; }
                    static String fromField() { return "x" + field; }
                    static String fromParameter(String s) { String t = s; return t + "!"; }
                    static String mk() { return new String("m"); }
                    static String fromCall() { String s = mk(); return s + "!"; }
                    static String fromBranch(boolean b) { String s = b ? "y" : field; return s + "!"; }
                    static String ctorOfField() { return new String(field); }
                    static String internOfField() { return field.intern(); }
                    @InterpretEntry
                    static void ok() { check(true); }
                }
                """);
        assertEquals(0, ran.verificationResults().verificationFailedMethods(), () -> messages(ran).toString());
        String prefix = "error: not supported by the direct Cortex translation: ";
        String mayBeNull = prefix + BytecodeToCortexCompiler.MAYBE_NULL_STRING;
        assertEquals(List.of(
                prefix + "string conversion of int in a concatenation",
                prefix + "invokedynamic run",
                mayBeNull, mayBeNull, mayBeNull, mayBeNull, mayBeNull, mayBeNull), messages(ran));
    }

    /**
     * Never-null follows the path: a local reassigned from a constant after holding a field, a
     * value merged from two constants, and a local updated in a loop by concatenation are accepted.
     */
    @Test
    void nonNullIsProvenAlongThePath(@TempDir Path tmp) throws Exception {
        assertPasses(interpret(tmp, "Paths.java", "Paths#run", """
                import org.strata.jverify.InterpretEntry;
                import static org.strata.jverify.JVerify.check;

                class Paths {
                    static String field = "f";
                    @InterpretEntry
                    static void run() {
                        { String a = field; check(a.length() == 1); }     // javac may reuse a's slot for b
                        { String b = "lit"; check((b + "!").equals("lit!")); }
                        boolean c = field.length() == 1;
                        String t = c ? "a" : "b";                            // merged from two constants
                        check((t + "!").equals("a!"));
                        String s = "x";
                        for (int i = 0; i < 3; i++) {
                            s = s + "y";                                     // every store to s is never null
                        }
                        check(s.equals("xyyy"));
                        String r = field;
                        r = "z";                                             // the last store on the path wins
                        check((r + r).equals("zz"));
                    }
                }
                """));
    }

    /**
     * The writes to the pool, or to {@code $value} of an object the procedure
     * did not allocate, in {@code proc}. "Allocated" is a forward must-analysis
     * over the blocks: a variable is fresh after {@code new String} and copies
     * of it, until it is reassigned; at a join only what every path has.
     */
    private static List<String> poolWritesOutsideConstructors(Procedure proc, Set<String> poolGlobals) {
        var body = proc.body().get();
        var byLabel = new java.util.HashMap<String, org.strata.jverify.cortex.Block>();
        var succs = new java.util.HashMap<String, List<String>>();
        for (var b : body.blocks()) {
            byLabel.put(b.label(), b);
            succs.put(b.label(), switch (b.transfer()) {
                case org.strata.jverify.cortex.Transfer.Goto g -> List.of(g.target());
                case org.strata.jverify.cortex.Transfer.CondGoto c -> List.of(c.thenTarget(), c.elseTarget());
                case org.strata.jverify.cortex.Transfer.Finish f -> List.of();
            });
        }
        var in = new java.util.HashMap<String, Set<String>>();
        in.put(body.entry(), new HashSet<>());
        for (var h : body.handlers()) {
            in.put(h.target(), new HashSet<>());
        }
        var work = new java.util.ArrayDeque<>(in.keySet());
        while (!work.isEmpty()) {
            String l = work.poll();
            var out = simulate(byLabel.get(l), in.get(l), poolGlobals, null);
            for (String s : succs.get(l)) {
                var before = in.get(s);
                if (before == null) {
                    in.put(s, new HashSet<>(out));
                    work.add(s);
                } else if (before.retainAll(out)) {
                    work.add(s);
                }
            }
        }
        var violations = new ArrayList<String>();
        for (var b : body.blocks()) {
            if (in.containsKey(b.label())) {
                simulate(b, in.get(b.label()), poolGlobals, v -> violations.add(proc.name() + ": " + v));
            }
        }
        return violations;
    }

    private static Set<String> simulate(org.strata.jverify.cortex.Block block, Set<String> freshIn,
                                        Set<String> poolGlobals, java.util.function.Consumer<String> report) {
        var fresh = new HashSet<>(freshIn);
        for (var cmd : block.cmds()) {
            switch (cmd) {
                case Cmd.New n when n.ty().equals(BytecodeToCortexCompiler.STRING) -> fresh.add(n.lhs());
                case Cmd.New n -> fresh.remove(n.lhs());
                case Cmd.Set st -> {
                    if (poolGlobals.contains(st.name()) && report != null) {
                        report.accept("writes " + st.name());
                    }
                    if (st.value().isPresent() && st.value().get() instanceof Expr.Var v && fresh.contains(v.name())) {
                        fresh.add(st.name());
                    } else {
                        fresh.remove(st.name());
                    }
                }
                case Cmd.WriteField w when w.owner().equals(BytecodeToCortexCompiler.STRING_POOL) -> {
                    if (report != null) {
                        report.accept("writes a pool node");
                    }
                }
                case Cmd.WriteField w when w.owner().equals(BytecodeToCortexCompiler.STRING)
                        && !(w.obj() instanceof Expr.Var v && fresh.contains(v.name())) -> {
                    if (report != null) {
                        report.accept("writes $value of an object it did not allocate");
                    }
                }
                case Cmd.Call c -> fresh.removeAll(c.lhs());
                case Cmd.VirtualCall c -> fresh.removeAll(c.lhs());
                case Cmd.ReadField r -> fresh.remove(r.lhs());
                case Cmd.Init i -> fresh.remove(i.name());
                default -> {
                }
            }
        }
        return fresh;
    }
}
