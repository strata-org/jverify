package org.strata.java.bytecode.verifier.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.Body;
import org.strata.jverify.laurel.Procedure;
import org.strata.jverify.laurel.Program;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.laurel.TypeDefinition;
import org.strata.jverify.verifier.SourceFile;
import org.strata.java.bytecode.frontend.BytecodeFrontend;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Laurel-level shape of the array translation (no Strata): where array
 * statements may be emitted (design §4.6), and reads on receivers that are
 * not locals staying expressions.
 */
class ArrayTranslationTest {

    private static Path libraryJar() {
        return Path.of(java.util.Objects.requireNonNull(
                System.getProperty("bytecode.library.jar"),
                "bytecode.library.jar system property not set"));
    }

    private record Translation(Program program, List<String> messages) {
    }

    private static LibraryContracts objectContracts() {
        return LibraryContracts.of(LibraryContracts.objectContractSource(), List.of(), List.of(libraryJar()));
    }

    private static Translation translate(Path tmp, String className, String source) throws Exception {
        return translate(tmp, className, source, objectContracts());
    }

    private static Translation translate(Path tmp, String className, String source, LibraryContracts contracts)
            throws Exception {
        Path classes = Files.createDirectories(tmp.resolve("classes-" + className));
        var file = new SourceFile(tmp.resolve(className + ".java"), source);
        var compiled = BytecodeFrontend.compile(List.of(file), List.of(libraryJar()), classes, Writer.nullWriter());
        assertTrue(compiled.success(), () -> "javac failed: " + compiled.diagnostics());
        return analyze(file, classes, contracts);
    }

    private static Translation analyze(SourceFile file, Path classes, LibraryContracts contracts) throws Exception {
        var reporter = new Reporter();
        var result = new BytecodeToLaurelCompiler(reporter, true, List.of(libraryJar()), false, false, contracts)
                .analyze(List.of(file.toUri()), BytecodeFrontend.readClasses(classes));
        return new Translation(result.program(),
                reporter.diagnostics().stream().map(d -> d.getMessage(null)).toList());
    }

    /** Compile with default javac flags only (no {@code -g}: no LocalVariableTable). */
    private static Translation translateWithoutDebugInfo(Path tmp, String className, String source,
                                                         LibraryContracts contracts) throws Exception {
        Path classes = Files.createDirectories(tmp.resolve("bare-" + className
                + (contracts.disabled() ? "-none" : "-contracts")));
        var file = new SourceFile(tmp.resolve(className + ".java"), source);
        var compiler = ToolProvider.getSystemJavaCompiler();
        var collector = new DiagnosticCollector<JavaFileObject>();
        try (var fm = compiler.getStandardFileManager(collector, null, null)) {
            fm.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classes));
            fm.setLocationFromPaths(StandardLocation.CLASS_PATH, List.of(libraryJar()));
            assertTrue(compiler.getTask(new PrintWriter(new StringWriter()), fm, collector,
                            List.of("-proc:none"), null, List.of(file)).call(),
                    () -> "javac failed: " + collector.getDiagnostics());
        }
        return analyze(file, classes, contracts);
    }

    private static boolean hasProcedure(Program p, String name) {
        try {
            procedure(p, name);
            return true;
        } catch (AssertionError e) {
            return false;
        }
    }

    /**
     * Translate {@code methods} next to {@code static int ok(int[] a)} in
     * class {@code className}: exactly one refusal, {@code expected}, for the
     * method {@code refused}, and {@code ok} still translates.
     */
    private static void assertRefusedBesideOk(Path tmp, String className, String methods, String refused,
                                              String expected) throws Exception {
        var t = translate(tmp, className, """
                import static org.strata.jverify.JVerify.*;
                import java.util.function.IntFunction;
                public class %s {
                %s
                    static int ok(int[] a) {
                        precondition(a.length > 0);
                        return a[0];
                    }
                }
                """.formatted(className, methods.indent(4)));
        assertEquals(List.of(expected), t.messages());
        assertTrue(hasProcedure(t.program(), className + "_ok"), "ok must still translate");
        assertFalse(hasProcedure(t.program(), refused), refused + " must be refused");
    }

    private static void assertStaticRefusal(Path tmp, String className, String methods, String reason)
            throws Exception {
        assertRefusedBesideOk(tmp, className, methods, className + "_m", "error: " + reason);
    }

    private static Procedure procedure(Program p, String name) {
        var all = new ArrayList<>(p.staticProcedures());
        for (var t : p.types()) {
            if (t instanceof TypeDefinition.Composite c) {
                all.addAll(c.ty().instanceProcedures());
            }
        }
        return all.stream().filter(q -> q.name().text().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no procedure " + name));
    }

    private static List<AstNode<StmtExpr>> implementation(Procedure proc) {
        var opaque = assertInstanceOf(Body.Opaque.class, proc.body());
        var block = assertInstanceOf(StmtExpr.Block.class, opaque.implementation().orElseThrow().val());
        return block.statements();
    }

    private static boolean callsIn(AstNode<StmtExpr> n, String callee) {
        if (n.val() instanceof StmtExpr.StaticCall c && c.callee().text().equals(callee)) {
            return true;
        }
        for (var child : BodyTranslator.childrenOf(n.val())) {
            if (callsIn(child, callee)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void allocationInATernaryArmRefusesConditional(@TempDir Path tmp) throws Exception {
        var t = translate(tmp, "Cond", """
                public class Cond {
                    static int c(boolean c) {
                        int[] a = c ? new int[1] : new int[2];
                        return a.length;
                    }
                    static int ok(int x) { return x; }
                }
                """);
        assertEquals(List.of("error: " + ArrayModel.CONDITIONAL), t.messages());
        procedure(t.program(), "Cond_ok");
    }

    @Test
    void allocationAfterAFieldReadRefusesOrder(@TempDir Path tmp) throws Exception {
        var t = translate(tmp, "Order", """
                public class Order {
                    int x;
                    static int bump() { return 1; }
                    int m() {
                        return this.x + new int[bump()].length;
                    }
                }
                """);
        assertEquals(List.of("warning: instance method 'm' was not verified: " + ArrayModel.ORDER), t.messages());
    }

    @Test
    void readOfACallResultStaysAnExpression(@TempDir Path tmp) throws Exception {
        var t = translate(tmp, "Read", """
                public class Read {
                    int x;
                    static int[] mk() { return new int[1]; }
                    int r() {
                        return this.x + mk()[0];
                    }
                }
                """);
        assertEquals(List.of(), t.messages());
        var stmts = implementation(procedure(t.program(), "r"));
        assertEquals(1, stmts.size(), stmts::toString);
        var ret = assertInstanceOf(StmtExpr.Return.class, stmts.get(0).val());
        assertTrue(callsIn(ret.value().orElseThrow(), ArrayModel.GET_WRAPPER));
    }

    @Test
    void fieldLengthInALoopConditionStaysInTheCondition(@TempDir Path tmp) throws Exception {
        var t = translate(tmp, "Loop", """
                public class Loop {
                    int[] f;
                    int w() {
                        int i = 0;
                        while (i < this.f.length) {
                            i++;
                        }
                        return i;
                    }
                }
                """);
        assertEquals(List.of(), t.messages());
        var loop = implementation(procedure(t.program(), "w")).stream()
                .filter(s -> s.val() instanceof StmtExpr.While).findFirst().orElseThrow();
        assertTrue(callsIn(((StmtExpr.While) loop.val()).cond(), ArrayModel.LENGTH_WRAPPER));
    }

    @Test
    void elementReadInALoopConditionRefuses(@TempDir Path tmp) throws Exception {
        // Strata evaluates the calls of a While condition once, before the
        // loop; length reads and a read in the latch test stay allowed.
        var t = translate(tmp, "LoopRead", """
                public class LoopRead {
                    int[] f;
                    static int w(int[] a) {
                        int i = 0;
                        while (a[i] != 0) {
                            i++;
                        }
                        return i;
                    }
                    static int forLoop(int[] a) {
                        int i;
                        int s = 0;
                        for (i = 0; a[i] != 0; i++) {
                            s++;
                        }
                        return s;
                    }
                    static void z(boolean[] z) {
                        while (z[0]) {
                            z[0] = false;
                        }
                    }
                    int field() {
                        int i = 0;
                        while (this.f[i] != 0) {
                            i++;
                        }
                        return i;
                    }
                    static int len(int[] a) {
                        int i = 0;
                        while (i < a.length) {
                            i++;
                        }
                        return i;
                    }
                    static int latch(int[] a) {
                        int i = 0;
                        do {
                            i++;
                        } while (a[i] != 0);
                        return i;
                    }
                }
                """);
        assertEquals(List.of("error: " + ArrayModel.LOOP_CONDITION_READ, "error: " + ArrayModel.LOOP_CONDITION_READ,
                "error: " + ArrayModel.LOOP_CONDITION_READ,
                "warning: instance method 'field' was not verified: " + ArrayModel.LOOP_CONDITION_READ),
                t.messages());
        assertTrue(hasProcedure(t.program(), "LoopRead_len"));
        assertTrue(hasProcedure(t.program(), "LoopRead_latch"));
    }

    @Test
    void writeInASingleBlockDoWhileBodyTranslates(@TempDir Path tmp) throws Exception {
        // javac puts the whole body in the latch block, with the test.
        var t = translate(tmp, "DoWrite", """
                import static org.strata.jverify.JVerify.*;
                public class DoWrite {
                    static void d(int[] a) {
                        precondition(a.length == 3);
                        int i = 0;
                        do {
                            invariant(0 <= i && i < 3);
                            a[i] = 1;
                            i++;
                        } while (i < 3);
                    }
                }
                """);
        assertEquals(List.of(), t.messages());
        assertTrue(hasProcedure(t.program(), "DoWrite_d"));
    }

    @Test
    void allocationInALoopGetsTheFallbackFrame(@TempDir Path tmp) throws Exception {
        var t = translate(tmp, "AllocFrame", """
                import static org.strata.jverify.JVerify.*;
                public class AllocFrame {
                    static void inLoop(int n) {
                        for (int i = 0; i < n; i++) {
                            invariant(0 <= i);
                            int[] a = new int[2];
                        }
                    }
                    static void once() {
                        int[] a = new int[2];
                    }
                }
                """);
        assertEquals(List.of(), t.messages());
        assertEquals(List.of(new StmtExpr.All()), modifiesTargets(procedure(t.program(), "AllocFrame_inLoop")));
        assertEquals(List.of(), modifiesTargets(procedure(t.program(), "AllocFrame_once")));
    }

    private static List<StmtExpr> modifiesTargets(Procedure proc) {
        var opaque = assertInstanceOf(Body.Opaque.class, proc.body());
        return opaque.modifies().stream().flatMap(g -> g.targets().stream()).map(AstNode::val).toList();
    }

    @Test
    void allocationInADoWhileTestWithAContinueRefusesConditional(@TempDir Path tmp) throws Exception {
        // The continue skips the test's statements, which join the body.
        var t = translate(tmp, "DoCont", """
                public class DoCont {
                    static int d() {
                        int i = 0;
                        do {
                            if (i == 1) {
                                i = 2;
                                continue;
                            }
                            i++;
                        } while (i < new int[3].length);
                        return i;
                    }
                    static int e() {
                        int i = 0;
                        do {
                            i++;
                        } while (i < new int[3].length);
                        return i;
                    }
                }
                """);
        assertEquals(List.of("error: " + ArrayModel.CONDITIONAL), t.messages());
        assertTrue(hasProcedure(t.program(), "DoCont_e"), "without a continue the test runs once per iteration");
    }

    @Test
    void allocationInAHeaderSpecificationRefusesSpecWrite(@TempDir Path tmp) throws Exception {
        var t = translate(tmp, "SpecAlloc", """
                import static org.strata.jverify.JVerify.*;
                public class SpecAlloc {
                    static int s(int n) {
                        precondition(new int[2].length == 2);
                        return n;
                    }
                }
                """);
        assertEquals(List.of("error: " + ArrayModel.SPEC_WRITE), t.messages());
    }

    @Test
    void elementAssignmentUsedAsAValueRefusesValueUse(@TempDir Path tmp) throws Exception {
        var t = translate(tmp, "ValueUse", """
                public class ValueUse {
                    static int post(int[] a) {
                        return a[0]++;
                    }
                    static int assign(int[] a) {
                        int x = (a[0] = 3);
                        return x;
                    }
                }
                """);
        assertEquals(List.of("error: " + ArrayModel.VALUE_USE, "error: " + ArrayModel.VALUE_USE), t.messages());
    }

    @Test
    void nullableArraysRefusePerMethod(@TempDir Path tmp) throws Exception {
        var t = translate(tmp, "Nulls", """
                import org.strata.jverify.Nullable;
                public class Nulls {
                    int @Nullable [] f;
                    static int param(@Nullable int[] a) { return 0; }
                    static @Nullable int[] ret() { return new int[1]; }
                    int field() { return f.length; }
                    static int ok(int[] a) { return 0; }
                }
                """);
        assertEquals(List.of("error: " + ArrayModel.NULLABLE, "error: " + ArrayModel.NULLABLE,
                "warning: instance method 'field' was not verified: " + ArrayModel.NULLABLE), t.messages());
        procedure(t.program(), "Nulls_ok");
    }

    @Test
    void allocationInAPureMethodRefusesPureWrite(@TempDir Path tmp) throws Exception {
        var t = translate(tmp, "PureAlloc", """
                import org.strata.jverify.Pure;
                public class PureAlloc {
                    @Pure static int p() {
                        int[] a = new int[2];
                        return a.length;
                    }
                }
                """);
        assertEquals(List.of("error: " + ArrayModel.PURE_WRITE), t.messages());
    }

    // --- Out-of-scope constructs: one per-method refusal each (design §6) ---

    @Test
    void referenceArrayAllocationRefusesReference(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "RefNew", "static int m() { return new String[2].length; }",
                ArrayModel.REFERENCE);
    }

    @Test
    void referenceArrayParameterRefusesReference(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "RefParam", "static int m(String[] s) { return 0; }", ArrayModel.REFERENCE);
    }

    @Test
    void referenceArrayFieldRefusesTheAccessingMethod(@TempDir Path tmp) throws Exception {
        assertRefusedBesideOk(tmp, "RefField", """
                String[] names;
                int m() { return names.length; }
                """, "m", "warning: instance method 'm' was not verified: " + ArrayModel.REFERENCE);
    }

    @Test
    void multiDimensionalAllocationRefusesMulti(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "MultiNew", "static int m() { return new int[2][3].length; }", ArrayModel.MULTI);
    }

    @Test
    void arrayOfArraysAllocationRefusesMulti(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "MultiRow", "static int m() { return new int[2][].length; }", ArrayModel.MULTI);
    }

    @Test
    void multiDimensionalParameterRefusesMulti(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "MultiParam", "static int m(int[][] a) { return 0; }", ArrayModel.MULTI);
    }

    @Test
    void floatArrayRefusesItsType(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "FloatNew", "static int m() { return new float[2].length; }",
                "Unsupported type: float[]");
    }

    @Test
    void doubleArrayParameterRefusesItsType(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "DoubleParam", "static int m(double[] d) { return 0; }",
                "Unsupported type: double[]");
    }

    @Test
    void returnedArrayConstructorReferenceRefusesCtorRef(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "CtorRef", "static IntFunction<int[]> m() { return int[]::new; }",
                ArrayModel.CTOR_REF);
    }

    @Test
    void storedArrayConstructorReferenceRefusesCtorRef(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "CtorRefLocal", """
                static IntFunction<int[]> m() {
                    IntFunction<int[]> f = int[]::new;
                    return f;
                }
                """, ArrayModel.CTOR_REF);
    }

    @Test
    void arraycopyRefusesArraycopy(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "Copy", "static void m(int[] a, int[] b) { System.arraycopy(a, 0, b, 0, 1); }",
                ArrayModel.ARRAYCOPY);
    }

    @Test
    void javaUtilArraysRefusesArraysUtil(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "Fill", "static void m(int[] a) { java.util.Arrays.fill(a, 0); }",
                ArrayModel.ARRAYS_UTIL);
    }

    @Test
    void cloneRefusesClone(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "Clone", "static int m(int[] a) { int[] b = a.clone(); return b.length; }",
                ArrayModel.CLONE);
    }

    @Test
    void arrayIntoAnObjectLocalRefusesAsObject(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "AsLocal", "static int m(int[] a) { Object o = a; return 0; }",
                ArrayModel.AS_OBJECT);
    }

    @Test
    void arrayAsAnObjectArgumentRefusesAsObject(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "AsArg", """
                static void takesObject(Object o) { }
                static void m(int[] a) { takesObject(a); }
                """, ArrayModel.AS_OBJECT);
    }

    @Test
    void arrayInAStringConcatenationRefusesAsObject(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "AsConcat", "static String m(int[] a) { return \"x\" + a; }",
                ArrayModel.AS_OBJECT);
    }

    @Test
    void ternaryOfDifferentArrayTypesRefusesAsObject(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "AsTernary", """
                static int m(boolean c, int[] a, long[] b) {
                    Object o = c ? a : b;
                    return 0;
                }
                """, ArrayModel.AS_OBJECT);
    }

    @Test
    void ternaryOfOneArrayTypeKeepsItsElementKind(@TempDir Path tmp) throws Exception {
        var t = translate(tmp, "SameTernary", """
                import static org.strata.jverify.JVerify.*;
                public class SameTernary {
                    static int m(boolean c, int[] a, int[] b) {
                        precondition(a.length > 0 && b.length > 0);
                        return (c ? a : b)[0];
                    }
                }
                """);
        assertEquals(List.of(), t.messages());
        assertTrue(callsIn(implementation(procedure(t.program(), "SameTernary_m")).get(0),
                ArrayModel.GET_WRAPPER));
    }

    @Test
    void arrayAsAReceiverRefusesAsObject(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "AsRecv", "static int m(int[] a) { return a.hashCode(); }", ArrayModel.AS_OBJECT);
    }

    @Test
    void arrayReturnedAsObjectRefusesAsObject(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "AsReturn", "static Object m(int[] a) { return a; }", ArrayModel.AS_OBJECT);
    }

    @Test
    void arrayIntoAnObjectFieldRefusesAsObject(@TempDir Path tmp) throws Exception {
        assertRefusedBesideOk(tmp, "AsField", """
                Object o;
                void m(int[] a) { this.o = a; }
                """, "m", "warning: instance method 'm' was not verified: " + ArrayModel.AS_OBJECT);
    }

    @Test
    void jequalsOnArraysRefusesAsObject(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "AsJequals", "static boolean m(int[] a, int[] b) { return jequals(a, b); }",
                ArrayModel.AS_OBJECT);
    }

    @Test
    void castToAnArrayTypeRefusesCast(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "CastTo", "static int m(Object o) { int[] a = (int[]) o; return a.length; }",
                ArrayModel.CAST);
    }

    @Test
    void instanceofAnArrayTypeRefusesCast(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "InstOf", "static boolean m(Object o) { return o instanceof int[]; }",
                ArrayModel.CAST);
    }

    @Test
    void arrayTypedQuantifierBinderRefusesQuantifier(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "Quant", "static void m() { check(forall((int[] x) -> x.length >= 0)); }",
                ArrayModel.QUANTIFIER);
    }

    @Test
    void enhancedForRefusesUntypedLocal(@TempDir Path tmp) throws Exception {
        assertStaticRefusal(tmp, "ForEach", """
                static int m(int[] a) {
                    int s = 0;
                    for (int x : a) {
                        s = x;
                    }
                    return s;
                }
                """, ArrayModel.UNTYPED_LOCAL);
    }

    @Test
    void codeWithoutDebugInfoRefusesArrayMethodsOnly(@TempDir Path tmp) throws Exception {
        String source = """
                public class Bare {
                    static int first(int[] a) { return a[0]; }
                    static int alloc(int n) { int k = n; return new int[2].length + k; }
                    static int[] mk() { return new int[3]; }
                    static int plain(int x) { int y = x + 1; return y; }
                }
                """;
        var t = translateWithoutDebugInfo(tmp, "Bare", source, objectContracts());
        assertEquals(List.of("error: " + ArrayModel.NO_DEBUG_INFO, "error: " + ArrayModel.NO_DEBUG_INFO),
                t.messages().stream().filter(m -> !m.contains("input compiled without")).toList());
        // No locals to type: an array method with no slots translates.
        procedure(t.program(), "Bare_mk");
        var baseline = translateWithoutDebugInfo(tmp, "Bare", source, LibraryContracts.none());
        assertEquals(procedure(baseline.program(), "Bare_plain"), procedure(t.program(), "Bare_plain"));
    }

    @Test
    void allocationInLibraryContractCodeRefusesContractWrite(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("contracts/q"));
        Files.writeString(tmp.resolve("contracts/q/MathContract.java"), """
                package q;
                import org.strata.jverify.Contract;
                import org.strata.jverify.Pure;
                @Contract(Math.class)
                class MathContract {
                    @Pure
                    public static int abs(int a) {
                        int[] t = new int[1];
                        return a < 0 ? -a : a;
                    }
                }
                """);
        var sources = LibraryContracts.collectUserSources(tmp, List.of(Path.of("contracts")));
        var contracts = LibraryContracts.of(LibraryContracts.objectContractSource(), sources, List.of(libraryJar()));
        var t = translate(tmp, "UsesAbs", """
                public class UsesAbs {
                    static int m(int x) { return Math.abs(x); }
                    static int ok(int[] a) { return a.length; }
                }
                """, contracts);
        assertEquals(List.of("error: call to 'abs', which could not be translated: " + ArrayModel.CONTRACT_WRITE),
                t.messages());
        procedure(t.program(), "UsesAbs_ok");
    }

    /** With no contracts the array model is off: every construct keeps its pre-model refusal. */
    @Test
    void withoutContractsArrayConstructsKeepTodaysTexts(@TempDir Path tmp) throws Exception {
        var t = translate(tmp, "Legacy", """
                public class Legacy {
                    static void alloc() { Object o = new int[2]; }
                    static int read() { return (new int[2])[0]; }
                    static void write() { (new int[2])[0] = 1; }
                    static int len() { return (new int[2]).length; }
                    static int param(int[] a) { return 0; }
                    static int ref() { return new String[2].length; }
                    static int multi() { return new int[2][3].length; }
                    static boolean inst(Object o) { return o instanceof int[]; }
                    static IntFunction<int[]> ctor() { return int[]::new; }
                    static void copy(Object a, Object b) { System.arraycopy(a, 0, b, 0, 1); }
                }
                """.replace("public class", "import java.util.function.IntFunction;\npublic class"),
                LibraryContracts.none());
        assertEquals(List.of(
                "error: Unsupported expression: ArrayCreationExpr",
                "error: Unsupported expression: ArrayAccessExpr",
                "error: Unsupported variable expression: ArrayAccessExpr",
                "error: Unsupported expression: FieldAccessExpr",
                "error: Unsupported type: int[]",
                "error: Unsupported expression: FieldAccessExpr",
                "error: Unsupported expression: FieldAccessExpr",
                "error: instanceof on opaque reference types is not yet supported",
                "error: Call to unknown class 'System' is not supported"),
                t.messages());
        // A returned lambda allocates its functional interface's sort.
        procedure(t.program(), "Legacy_ctor");
    }
}
