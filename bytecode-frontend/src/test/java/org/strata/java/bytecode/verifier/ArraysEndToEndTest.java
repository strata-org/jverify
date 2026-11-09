package org.strata.java.bytecode.verifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.Body;
import org.strata.jverify.laurel.HighType;
import org.strata.jverify.laurel.Procedure;
import org.strata.jverify.laurel.LaurelProgram;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.laurel.TypeDefinition;
import org.strata.jverify.laurel.Variable;
import org.strata.jverify.verifier.DiagnosticWithRange;
import org.strata.jverify.verifier.JVerifyResults;
import org.strata.jverify.verifier.SourceFile;
import org.strata.java.bytecode.frontend.BytecodeFrontend;
import org.strata.java.bytecode.verifier.compiler.BytecodeToLaurelCompiler;
import org.strata.java.bytecode.verifier.compiler.LibraryContracts;
import org.strata.java.bytecode.verifier.compiler.Reporter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Primitive arrays on the full pipeline (javac -> classfiles -> Laurel ->
 * Strata) through the {@link VerifierDriver#analyzeClassesDir} seam: the
 * JArray model is not vacuous, programs without array writes verify, the
 * emitted program has the model's shape, and {@link LibraryContracts#none()}
 * keeps today's refusals.
 */
class ArraysEndToEndTest {

    private static Path libraryJar() {
        return org.strata.java.bytecode.verifier.VerifierDriver.specLibraryLocation();
    }

    private static VerifierOptions options(boolean continueOnErrors) {
        return new VerifierOptions(
                new PrintWriter(new StringWriter(), true),
                Path.of(System.getProperty("user.dir")),
                List.of(libraryJar()),
                null, false, true, List.of(), false,
                true, continueOnErrors, false, false, false, null, null, false);
    }

    private static LibraryContracts objectContracts() {
        return LibraryContracts.of(LibraryContracts.objectContractSource(), List.of(), List.of(libraryJar()));
    }

    private record Compiled(Path classes, URI uri) {
    }

    private static Compiled compile(Path tmp, String className, String source) throws Exception {
        Path classes = Files.createTempDirectory(tmp, "classes-" + className);
        var file = new SourceFile(tmp.resolve(className + ".java"), source);
        var result = BytecodeFrontend.compile(List.of(file), List.of(libraryJar()), classes, Writer.nullWriter());
        assertTrue(result.success(), () -> "javac failed: " + result.diagnostics());
        return new Compiled(classes, file.toUri());
    }

    private static JVerifyResults run(Compiled program, LibraryContracts contracts, boolean continueOnErrors)
            throws Exception {
        return new VerifierDriver(options(continueOnErrors)).analyzeClassesDir(
                program.classes(), List.of(program.uri()), new Reporter(), false, contracts, Map.of());
    }

    private static JVerifyResults run(Path tmp, String className, String source) throws Exception {
        return run(compile(tmp, className, source), objectContracts(), true);
    }

    /** "<line> <message>" per diagnostic, in order. */
    private static List<String> diagnostics(JVerifyResults results) {
        return results.diagnostics().stream()
                .map(d -> (d instanceof DiagnosticWithRange w ? w.getRange().start().line() : -1)
                        + " " + d.getMessage(null))
                .toList();
    }

    private static List<String> messages(JVerifyResults results) {
        return results.diagnostics().stream().map(d -> d.getMessage(null)).toList();
    }

    private record Counts(int passed, int failed, int skipped, int failedAssertions) {
    }

    private static Counts counts(JVerifyResults results) {
        var v = results.verdictOrNull();
        return v == null ? null : new Counts(v.verificationPassedMethods(), v.verificationFailedMethods(),
                v.verificationSkippedMethods(), v.verificationFailedAssertions());
    }

    private static JVerifyResults.Ran assertRan(JVerifyResults results) {
        return assertInstanceOf(JVerifyResults.Ran.class, results, () -> diagnostics(results).toString());
    }

    /** Assert the program verifies: exit 0, no diagnostics, {@code verified} methods. */
    private static void assertVerifies(JVerifyResults results, int verified) {
        var ran = assertRan(results);
        assertEquals(List.of(), diagnostics(ran));
        assertEquals(0, ran.exitCode());
        assertEquals(new Counts(verified, 0, 0, 0), counts(ran));
    }

    private static int lineOf(String source, String needle) {
        var lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(needle)) {
                return i + 1;
            }
        }
        throw new AssertionError(needle);
    }

    // --- 1. The model is not vacuous: two writes then check(false) fails ---

    private static String vacuity(String check) {
        return """
                import static org.strata.jverify.JVerify.*;
                public class Vacuity {
                    static void v() {
                        int[] a = new int[2];
                        a[0] = 1;
                        a[0] = 2;
                        %s
                    }
                }
                """.formatted(check);
    }

    @Test
    void writesThenCheckFalseFailsOnce(@TempDir Path tmp) throws Exception {
        String source = vacuity("check(false);");
        var ran = assertRan(run(tmp, "Vacuity", source));
        assertEquals(4, ran.exitCode(), () -> diagnostics(ran).toString());
        assertEquals(List.of(lineOf(source, "check(false);") + " Error: assertion could not be proved"), diagnostics(ran));
        assertEquals(new Counts(1, 1, 0, 1), counts(ran));
    }

    @Test
    void lastWriteWins(@TempDir Path tmp) throws Exception {
        assertVerifies(run(tmp, "Vacuity", vacuity("check(a[0] == 2 && a[1] == 0);")), 2);
    }

    // --- 2. Programs with no array write (length determinism) ---

    private static final String NO_WRITE = """
            import static org.strata.jverify.JVerify.*;
            public class NoWrite {
                static void alloc(int n) {
                    precondition(n >= 0);
                    int[] a = new int[n];
                    check(a.length == n);
                }
                static int readParam(int[] a) {
                    precondition(a.length > 0);
                    return a[0];
                }
                static int[] make(int n) {
                    precondition(n >= 0);
                    postcondition((int[] r) -> r.length == n);
                    return new int[n];
                }
                static void useMake() {
                    int[] r = make(3);
                    check(r.length == 3);
                }
                static int count(int[] a) {
                    int i = 0;
                    while (i < a.length) {
                        invariant(0 <= i && i <= a.length);
                        i++;
                    }
                    return i;
                }
            }
            """;

    @Test
    void programsWithoutWritesVerify(@TempDir Path tmp) throws Exception {
        assertVerifies(run(tmp, "NoWrite", NO_WRITE), 6);
    }

    @Test
    void wrongLengthFailsOnce(@TempDir Path tmp) throws Exception {
        String source = """
                import static org.strata.jverify.JVerify.*;
                public class WrongLength {
                    static void alloc(int n) {
                        // The upper bound keeps `n + 1` free of its own
                        // overflow obligation; the test plants ONE failure.
                        precondition(n >= 0 && n <= 1000);
                        int[] a = new int[n];
                        check(a.length == n + 1);
                    }
                }
                """;
        var ran = assertRan(run(tmp, "WrongLength", source));
        assertEquals(4, ran.exitCode(), () -> diagnostics(ran).toString());
        assertEquals(List.of(lineOf(source, "check(a.length") + " Error: assertion could not be proved"),
                diagnostics(ran));
        assertEquals(new Counts(1, 1, 0, 1), counts(ran));
    }

    @Test
    void allocationInALoopWithoutWritesVerifies(@TempDir Path tmp) throws Exception {
        // The loop allocates, so it changes the heap: the method gets the
        // fallback frame, as a writing loop does.
        String source = """
                import static org.strata.jverify.JVerify.*;
                public class LoopAlloc {
                    static void f(int n) {
                        precondition(n >= 0 && n < 100);
                        for (int i = 0; i < n; i++) {
                            invariant(0 <= i);
                            int[] t = new int[2];
                            check(t.length == 2 && t[1] == 0);
                        }
                    }
                }
                """;
        assertVerifies(run(tmp, "LoopAlloc", source), 2);
    }

    // --- Element reads in a transparent @Transparent method ---

    @Test
    void pureElementReadVerifiesAtItsCallers(@TempDir Path tmp) throws Exception {
        // The transparent body becomes a function, where Strata cannot link
        // the opaque load helper: it reads the raw element instead.
        String source = """
                import org.strata.jverify.*;
                import static org.strata.jverify.JVerify.*;
                public class PureA {
                    @Transparent
                    static int first(int[] a) {
                        precondition(a.length > 0);
                        return a[0];
                    }
                    @Transparent
                    static boolean firstIsZero(int[] a) {
                        precondition(a.length > 0);
                        int x = a[0];
                        return x == 0;
                    }
                    static void use(int[] a) {
                        precondition(a.length > 0);
                        int v = first(a);
                        boolean z = firstIsZero(a);
                    }
                    static void useFresh() {
                        int[] a = new int[2];
                        a[0] = 7;
                        check(first(a) == 7);
                        check(!firstIsZero(a));
                    }
                }
                """;
        assertVerifies(run(tmp, "PureA", source), 5);
    }

    @Test
    void pureElementReadFailuresAreReported(@TempDir Path tmp) throws Exception {
        // An out-of-bounds read in the @Transparent body, and a wrong claim about
        // a correct one's result, still fail.
        String source = """
                import org.strata.jverify.*;
                import static org.strata.jverify.JVerify.*;
                public class PureOob {
                    @Transparent
                    static int unguarded(int[] a) {
                        return a[0];
                    }
                    @Transparent
                    static int first(int[] a) {
                        precondition(a.length > 0);
                        return a[0];
                    }
                    static void wrong() {
                        int[] a = new int[1];
                        check(first(a) == 1);
                    }
                }
                """;
        var ran = assertRan(run(tmp, "PureOob", source));
        assertEquals(4, ran.exitCode(), () -> diagnostics(ran).toString());
        assertEquals(List.of(
                lineOf(source, "return a[0];") + " Error: precondition does not hold",
                lineOf(source, "check(first(a) == 1);") + " Error: assertion could not be proved"),
                diagnostics(ran));
        assertEquals(new Counts(2, 2, 0, 2), counts(ran));
    }

    // --- Initializers and compound assignment ---

    @Test
    void initializerAndCompoundAssignmentVerify(@TempDir Path tmp) throws Exception {
        String source = """
                import static org.strata.jverify.JVerify.*;
                public class InitCompound {
                    static void init() {
                        int[] a = {1, 2, 3};
                        check(a.length == 3 && a[0] == 1 && a[2] == 3);
                    }
                    static void compound() {
                        int[] a = new int[2];
                        a[0] += 2;
                        a[1]++;
                        check(a[0] == 2 && a[1] == 1);
                    }
                }
                """;
        assertVerifies(run(tmp, "InitCompound", source), 3);
    }

    // --- Field arrays: wrappers, the write spill, field-granular frames ---

    @Test
    void fieldArrayWritesKeepOtherFields(@TempDir Path tmp) throws Exception {
        String source = """
                import static org.strata.jverify.JVerify.*;
                public class Fields {
                    int[] data;
                    int count;
                    void setFirst(int v) {
                        modifies(data);
                        precondition(data.length > 0);
                        data[0] = v;
                        check(data[0] == v);
                    }
                    void setFirstKeepsCount() {
                        modifies(data);
                        precondition(data.length > 0);
                        int c = count;
                        data[0] = 1;
                        check(count == c);
                    }
                }
                """;
        assertVerifies(run(tmp, "Fields", source), 3);
    }

    // --- 3. Program shape ---

    private static LaurelProgram translate(Compiled program, LibraryContracts contracts) throws Exception {
        return new BytecodeToLaurelCompiler(new Reporter(), true, List.of(libraryJar()), false, false, contracts)
                .analyze(List.of(program.uri()), BytecodeFrontend.readClasses(program.classes())).program();
    }

    /** Every procedure: static ones and every composite's instance procedures. */
    private static List<Procedure> allProcedures(LaurelProgram p) {
        var out = new ArrayList<>(p.staticProcedures());
        for (var t : p.types()) {
            if (t instanceof TypeDefinition.Composite c) {
                out.addAll(c.ty().instanceProcedures());
            }
        }
        return out;
    }

    /** Visit every expression node of a procedure (conditions, body, frames). */
    private static void forEachNode(Procedure proc, Consumer<StmtExpr> visit) {
        var roots = new ArrayList<AstNode<StmtExpr>>();
        proc.preconditions().forEach(c -> roots.add(c.condition()));
        switch (proc.body()) {
            case Body.Transparent t -> roots.add(t.body());
            case Body.Opaque o -> {
                o.postconditions().forEach(c -> roots.add(c.condition()));
                o.implementation().ifPresent(roots::add);
                o.modifies().forEach(g -> roots.addAll(g.targets()));
            }
            case Body.Abstract a -> a.postconditions().forEach(c -> roots.add(c.condition()));
            default -> { }
        }
        for (var r : roots) {
            walk(r, visit);
        }
    }

    private static void walk(AstNode<StmtExpr> n, Consumer<StmtExpr> visit) {
        visit.accept(n.val());
        for (var child : children(n.val())) {
            walk(child, visit);
        }
    }

    private static List<AstNode<StmtExpr>> children(StmtExpr v) {
        var out = new ArrayList<AstNode<StmtExpr>>();
        switch (v) {
            case StmtExpr.IfThenElse n -> {
                out.add(n.cond());
                out.add(n.thenBranch());
                n.elseBranch().ifPresent(out::add);
            }
            case StmtExpr.Block n -> out.addAll(n.statements());
            case StmtExpr.While n -> {
                out.add(n.cond());
                out.addAll(n.invariants());
                out.add(n.body());
            }
            case StmtExpr.Return n -> n.values().forEach(out::add);
            case StmtExpr.Var n -> {
                if (n.var_() instanceof Variable.Field f) {
                    out.add(f.target());
                }
            }
            case StmtExpr.Assign n -> out.add(n.value());
            case StmtExpr.Call n -> {
                n.receiver().ifPresent(out::add);
                out.addAll(n.arguments());
            }
            case StmtExpr.Quantifier n -> out.add(n.body());
            case StmtExpr.Old n -> out.add(n.value());
            case StmtExpr.Fresh n -> out.add(n.value());
            case StmtExpr.Assert n -> out.add(n.condition());
            case StmtExpr.Assume n -> out.add(n.condition());
            default -> { }
        }
        return out;
    }

    private static Procedure procedureNamed(LaurelProgram p, String name) {
        return allProcedures(p).stream().filter(q -> q.name().text().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no procedure " + name + " in "
                        + allProcedures(p).stream().map(q -> q.name().text()).toList()));
    }

    private static List<AstNode<StmtExpr>> modifiesTargets(Procedure proc) {
        var opaque = assertInstanceOf(Body.Opaque.class, proc.body());
        return opaque.modifies().stream().flatMap(g -> g.targets().stream()).toList();
    }

    private static void assertContentsVersion(AstNode<StmtExpr> target) {
        var v = assertInstanceOf(StmtExpr.Var.class, target.val());
        var f = assertInstanceOf(Variable.Field.class, v.var_());
        assertEquals("contentsVersion", f.fieldName().text());
    }

    @Test
    void emittedProgramHasTheModelShape(@TempDir Path tmp) throws Exception {
        String source = """
                import static org.strata.jverify.JVerify.*;
                public class Shape {
                    int[] data;
                    static void fill(int[] a) {
                        modifies(a);
                        precondition(a.length > 0);
                        a[0] = 1;
                    }
                    static int fresh() {
                        int[] a = new int[2];
                        a[0] = 1;
                        return a[1];
                    }
                    int first() {
                        precondition(data.length > 0);
                        return data[0];
                    }
                }
                """;
        LaurelProgram p = translate(compile(tmp, "Shape", source), objectContracts());
        var news = new ArrayList<StmtExpr.New>();
        var wrapperCalls = new ArrayList<StmtExpr.Call>();
        var names = new ArrayList<String>();
        boolean[] fresh = {false};
        for (var proc : allProcedures(p)) {
            names.add(proc.name().text());
            forEachNode(proc, n -> {
                switch (n) {
                    case StmtExpr.New nw when nw.ref().text().equals("JArray") -> news.add(nw);
                    case StmtExpr.Call c when c.receiver().isEmpty()
                            && (c.callee().text().startsWith("java?array_get")
                                || c.callee().text().startsWith("java?array_length")) -> wrapperCalls.add(c);
                    case StmtExpr.Call c when c.receiver().isEmpty() -> names.add(c.callee().text());
                    case StmtExpr.Fresh f -> fresh[0] = true;
                    default -> { }
                }
            });
        }
        assertEquals(1, news.size());
        assertEquals(List.of(new HighType.TInt()), news.get(0).typeArgs().stream().map(AstNode::val).toList());
        assertFalse(fresh[0], "no Fresh");
        assertFalse(names.stream().anyMatch(n -> n.contains("JArray::create")), names::toString);
        assertTrue(wrapperCalls.isEmpty(), wrapperCalls::toString);
        // The model's set and a program modifies(a) are field-granular.
        var setTargets = modifiesTargets(procedureNamed(p, "set"));
        assertEquals(1, setTargets.size());
        assertContentsVersion(setTargets.get(0));
        var fillTargets = modifiesTargets(procedureNamed(p, "Shape::fill"));
        assertEquals(1, fillTargets.size());
        assertContentsVersion(fillTargets.get(0));
        // The JArray composite is generic with the single ghost field.
        var jarray = p.types().stream()
                .filter(t -> t instanceof TypeDefinition.Composite c && c.ty().name().text().equals("JArray"))
                .map(t -> ((TypeDefinition.Composite) t).ty()).findFirst().orElseThrow();
        assertEquals(List.of("TArrayElement"), jarray.typeArgs().stream().map(i -> i.text()).toList());
        assertEquals(List.of("contentsVersion"), jarray.fields().stream().map(f -> f.name().text()).toList());
    }

    @Test
    void arrayFreeProgramEmitsNoArrayHelpers(@TempDir Path tmp) throws Exception {
        String source = """
                public class NoArrays {
                    static int add(int a, int b) { return a + b; }
                }
                """;
        LaurelProgram p = translate(compile(tmp, "NoArrays", source), objectContracts());
        for (var proc : allProcedures(p)) {
            assertFalse(proc.name().text().startsWith("java?array_"), proc.name().text());
        }
        assertFalse(p.types().stream().anyMatch(t -> t instanceof TypeDefinition.Composite c
                && c.ty().name().text().equals("JArray")));
    }

    // --- 4. none(): today's refusals ---

    @Test
    void noContractsKeepsTodaysRefusals(@TempDir Path tmp) throws Exception {
        String source = """
                import static org.strata.jverify.JVerify.*;
                public class Today {
                    static int alloc() {
                        int[] a = new int[2];
                        return 0;
                    }
                    static int read(int[] a) {
                        return a[0];
                    }
                    static int ok(int x) { return x; }
                }
                """;
        var program = compile(tmp, "Today", source);
        var ran = assertRan(run(program, LibraryContracts.none(), true));
        assertEquals(List.of(
                lineOf(source, "static int alloc") + " error: Unsupported type: int[]",
                lineOf(source, "static int read") + " error: Unsupported type: int[]"), diagnostics(ran));
        assertEquals(0, ran.exitCode());
        assertEquals(new Counts(2, 0, 2, 0), counts(ran));
        // With the model the same program verifies.
        var with = run(program, objectContracts(), true);
        assertEquals(List.of("Error: precondition does not hold"), messages(with));
    }
}
