package org.strata.java.bytecode.verifier.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.Body;
import org.strata.jverify.laurel.GotoDestination;
import org.strata.jverify.laurel.LaurelProgram;
import org.strata.jverify.laurel.Procedure;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.laurel.Variable;
import org.strata.jverify.verifier.SourceFile;
import org.strata.java.bytecode.frontend.BytecodeFrontend;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Laurel-level shape of loop emission (no Strata). Each loop is one labelled
 * block that keeps the loop header inside it, so the {@code invariant(...)}
 * markers follow the header statements -- implicit throw guards, chained
 * short-circuit tests, condition-embedded stores -- and must still be found;
 * a local first stored in the header must be declared outside the block; and
 * the back-edge {@code goto} must not displace a trailing if/else from tail
 * position.
 */
class LoopTranslationTest {

    private static Path libraryJar() {
        return org.strata.java.bytecode.verifier.VerifierDriver.specLibraryLocation();
    }

    private record Translation(LaurelProgram program, List<String> messages) {
    }

    private static Translation translate(Path tmp, String className, String source) throws Exception {
        Path classes = Files.createDirectories(tmp.resolve("classes-" + className));
        var file = new SourceFile(tmp.resolve(className + ".java"), source);
        var compiled = BytecodeFrontend.compile(List.of(file), List.of(libraryJar()), classes, Writer.nullWriter());
        assertTrue(compiled.success(), () -> "javac failed: " + compiled.diagnostics());
        return translateClasses(file, classes);
    }

    /** {@link #translate} compiled with {@code -g} only (the DEBUG tier: no CRT). */
    private static Translation translateDebugTier(Path tmp, String className, String source) throws Exception {
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        var collector = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();
        Path classes = Files.createDirectories(tmp.resolve("classes-g-" + className));
        var file = new SourceFile(tmp.resolve(className + ".java"), source);
        try (var fm = compiler.getStandardFileManager(collector, null, null)) {
            fm.setLocationFromPaths(javax.tools.StandardLocation.CLASS_OUTPUT, List.of(classes));
            fm.setLocationFromPaths(javax.tools.StandardLocation.CLASS_PATH, List.of(libraryJar()));
            assertTrue(compiler.getTask(Writer.nullWriter(), fm, collector,
                            List.of("-g", "-proc:none"), null, List.of(file)).call(),
                    () -> "javac failed: " + collector.getDiagnostics());
        }
        return translateClasses(file, classes);
    }

    private static Translation translateClasses(SourceFile file, Path classes) throws Exception {
        var reporter = new Reporter();
        var contracts = LibraryContracts.of(LibraryContracts.objectContractSource(), List.of(),
                List.of(libraryJar()));
        var result = new BytecodeToLaurelCompiler(reporter, true, List.of(libraryJar()), false, false, contracts)
                .analyze(List.of(file.toUri()), BytecodeFrontend.readClasses(classes));
        return new Translation(result.program(),
                reporter.diagnostics().stream().map(d -> d.getMessage(null)).toList());
    }

    private static AstNode<StmtExpr> bodyOf(LaurelProgram p, String name) {
        Procedure proc = p.staticProcedures().stream()
                .filter(x -> x.name().text().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError("no procedure " + name));
        var opaque = assertInstanceOf(Body.Opaque.class, proc.body());
        assertTrue(opaque.implementation().isPresent(), name + " has no implementation");
        return opaque.implementation().get();
    }

    /** All statement-position nodes under {@code n} (blocks, if arms, assign targets), pre-order. */
    private static List<AstNode<StmtExpr>> statements(AstNode<StmtExpr> n) {
        var out = new ArrayList<AstNode<StmtExpr>>();
        collect(n, out);
        return out;
    }

    private static void collect(AstNode<StmtExpr> n, List<AstNode<StmtExpr>> out) {
        out.add(n);
        switch (n.val()) {
            case StmtExpr.Block b -> b.statements().forEach(s -> collect(s, out));
            case StmtExpr.IfThenElse ite -> {
                collect(ite.thenBranch(), out);
                ite.elseBranch().ifPresent(e -> collect(e, out));
            }
            default -> {
            }
        }
    }

    /**
     * The labelled blocks a jump goes back to: one jumped to from inside it or from a
     * later statement, which Laurel's RestructureGotos makes a loop.
     */
    private static List<StmtExpr.Block> loopBlocks(AstNode<StmtExpr> body) {
        var all = statements(body);
        var loops = new ArrayList<StmtExpr.Block>();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).val() instanceof StmtExpr.Block b && b.label().isPresent()) {
                String l = b.label().get();
                if (all.subList(i + 1, all.size()).stream().anyMatch(s -> s.val() instanceof StmtExpr.Goto g
                        && g.target().equals(l))) {
                    loops.add(b);
                }
            }
        }
        return loops;
    }

    /** The single loop block of the procedure. */
    private static StmtExpr.Block loopBlock(AstNode<StmtExpr> body) {
        var labelled = loopBlocks(body);
        assertEquals(1, labelled.size(), "expected exactly one labelled loop block");
        return labelled.get(0);
    }

    /** The names declared under {@code n} ({@code var x} statements and declaring assign targets). */
    private static List<String> declaredNames(AstNode<StmtExpr> n) {
        var out = new ArrayList<String>();
        for (AstNode<StmtExpr> s : statements(n)) {
            if (s.val() instanceof StmtExpr.Var v && v.var_() instanceof Variable.Declare d) {
                out.add(d.parameter().name().text());
            }
            if (s.val() instanceof StmtExpr.Assign a) {
                for (var t : a.targets()) {
                    if (t.val() instanceof Variable.Declare d) {
                        out.add(d.parameter().name().text());
                    }
                }
            }
        }
        return out;
    }

    private static void assertLoopInvariants(Translation t, String proc, int count) {
        assertEquals(List.of(), t.messages());
        assertEquals(count, loopBlock(bodyOf(t.program(), proc)).invariants().size(),
                "loop block invariants of " + proc);
    }

    // --- The invariant markers behind extra header statements ---

    @Test
    void dividingConditionInvariantIsExtracted(@TempDir Path tmp) throws Exception {
        // The division's implicit divide-by-zero guard precedes the markers.
        var t = translate(tmp, "Div", """
                import static org.strata.jverify.JVerify.*;
                public class Div {
                    static int div(int d) {
                        precondition(d > 0);
                        int x = 100;
                        while (x / d > 0) {
                            invariant(x >= 0);
                            x = x - 1;
                        }
                        check(x >= 0);
                        return x;
                    }
                }
                """);
        assertLoopInvariants(t, "Div::div", 1);
    }

    @Test
    void shortCircuitAndInvariantIsExtracted(@TempDir Path tmp) throws Exception {
        // The second test's in-loop arm nests the markers.
        var t = translate(tmp, "AndCond", """
                import static org.strata.jverify.JVerify.*;
                public class AndCond {
                    static int f(int n, int m) {
                        int i = 0;
                        while (i < n && i < m) {
                            invariant(i >= 0);
                            i = i + 1;
                        }
                        return i;
                    }
                }
                """);
        assertLoopInvariants(t, "AndCond::f", 1);
    }

    @Test
    void shortCircuitOrInvariantIsExtractedOnce(@TempDir Path tmp) throws Exception {
        // || duplicates the body -- markers included -- into each test's arm:
        // every copy must be consumed, and the invariant kept only once.
        var t = translate(tmp, "OrCond", """
                import static org.strata.jverify.JVerify.*;
                public class OrCond {
                    static int f(int n, int m) {
                        int i = 0;
                        while (i < n || i < m) {
                            invariant(i >= 0);
                            i = i + 1;
                        }
                        return i;
                    }
                }
                """);
        assertLoopInvariants(t, "OrCond::f", 1);
    }

    @Test
    void compoundForInvariantIsExtracted(@TempDir Path tmp) throws Exception {
        var t = translate(tmp, "ForComp", """
                import static org.strata.jverify.JVerify.*;
                public class ForComp {
                    static int f(int n, int m) {
                        int s = 0;
                        for (int i = 0, j = 0; i < n && j < m; i = i + 1, j = j + 1) {
                            invariant(0 <= i && i == j && s == i);
                            s = s + 1;
                        }
                        return s;
                    }
                }
                """);
        assertLoopInvariants(t, "ForComp::f", 1);
    }

    @Test
    void plainWhileInvariantIsExtracted(@TempDir Path tmp) throws Exception {
        // The single-test baseline stays as before.
        var t = translate(tmp, "Plain", """
                import static org.strata.jverify.JVerify.*;
                public class Plain {
                    static int f(int n) {
                        int i = 0;
                        while (i < n) {
                            invariant(i >= 0);
                            i = i + 1;
                        }
                        return i;
                    }
                }
                """);
        assertLoopInvariants(t, "Plain::f", 1);
    }

    // --- Markers behind user statements are not loop contracts ---

    /** The translation refused the method as a misplaced contract call (no invariant promoted). */
    private static void assertMarkerRefused(Translation t, String proc) {
        assertEquals(1, t.messages().size(), () -> "messages: " + t.messages());
        assertTrue(t.messages().get(0).contains("call to contract method must come before the rest of the body")
                        || t.messages().get(0).contains("invariants are not allowed in a method"),
                () -> t.messages().get(0));
        assertTrue(t.program().staticProcedures().stream()
                        .noneMatch(p -> p.name().text().equals(proc)
                                && p.body() instanceof Body.Opaque o && o.implementation().isPresent()),
                proc + " must not be translated with the marker promoted");
    }

    @Test
    void markerAfterUserStatementIsRefused(@TempDir Path tmp) throws Exception {
        // `i = i + 5` is body, not condition lowering: the marker behind it
        // must not be promoted to the loop head, where it would be checked in
        // a state where the written-position claim never held.
        var t = translate(tmp, "AfterStmt", """
                import static org.strata.jverify.JVerify.*;
                public class AfterStmt {
                    static int m(int n) {
                        precondition(n >= 0 && n < 100);
                        int i = 0;
                        while (i < n) {
                            i = i + 5;
                            invariant(0 <= i && i <= n);
                            i = i - 4;
                        }
                        return i;
                    }
                }
                """);
        assertMarkerRefused(t, "AfterStmt::m");
    }

    @Test
    void markerInsideAUserIfIsRefused(@TempDir Path tmp) throws Exception {
        // `if (q)` is a user test, not one of the condition's: the scan must
        // not descend into it.
        var t = translate(tmp, "InsideIf", """
                import static org.strata.jverify.JVerify.*;
                public class InsideIf {
                    static int m(int n, boolean q) {
                        precondition(n >= 0 && n < 100);
                        int i = 0;
                        while (i < n) {
                            if (q) {
                                invariant(0 <= i && i <= n);
                            }
                            i = i + 1;
                        }
                        return i;
                    }
                }
                """);
        assertMarkerRefused(t, "InsideIf::m");
    }

    // --- A local first stored in the loop header ---

    @Test
    void headerStoredLocalIsDeclaredOutsideTheLoopBlock(@TempDir Path tmp) throws Exception {
        // The labelled block's scope ends at the loop exit, so `return x`
        // after the loop needs the declaration hoisted out of the block.
        var t = translate(tmp, "ReadAll", """
                public class ReadAll {
                    static int step(int i) { return i - 1; }
                    static int readAll(int seed) {
                        int x;
                        while ((x = step(seed)) != 0) {
                            seed = x;
                        }
                        return x;
                    }
                }
                """);
        assertEquals(List.of(), t.messages());
        AstNode<StmtExpr> body = bodyOf(t.program(), "ReadAll::readAll");
        StmtExpr.Block loop = loopBlock(body);
        assertFalse(declaredNames(new AstNode<>(loop, body.source())).contains("x"),
                "x must not be declared inside the loop block");
        assertTrue(declaredNames(body).contains("x"), "x must be declared in the method body");
    }

    @Test
    void invariantMayMentionAHeaderStoredLocal(@TempDir Path tmp) throws Exception {
        // Block invariants are resolved in the enclosing scope, so the
        // header-stored local must be visible to them too.
        var t = translate(tmp, "ReadInv", """
                import static org.strata.jverify.JVerify.*;
                public class ReadInv {
                    static int step(int i) { return i - 1; }
                    static int readAll(int seed) {
                        int x;
                        while ((x = step(seed)) != 0) {
                            invariant(x > 0 || x <= 0);
                            seed = x;
                        }
                        return x;
                    }
                }
                """);
        assertEquals(List.of(), t.messages());
        AstNode<StmtExpr> body = bodyOf(t.program(), "ReadInv::readAll");
        assertEquals(1, loopBlock(body).invariants().size());
        assertFalse(declaredNames(new AstNode<>(loopBlock(body), body.source())).contains("x"));
    }

    @Test
    void bodyStoredLocalUsedAfterTheLoopIsDeclaredOutsideTheLoopBlock(@TempDir Path tmp) throws Exception {
        // t's first store is in the loop BODY (it dominates the use after the
        // loop), but the labelled block's scope still ends at the loop exit.
        var t = translate(tmp, "BodyStore", """
                public class BodyStore {
                    static int g(int n) {
                        int i = 0;
                        int t;
                        while (true) {
                            i = i + 1;
                            t = i;
                            if (t > n) {
                                break;
                            }
                        }
                        return t;
                    }
                }
                """);
        assertEquals(List.of(), t.messages());
        AstNode<StmtExpr> body = bodyOf(t.program(), "BodyStore::g");
        StmtExpr.Block loop = loopBlock(body);
        assertFalse(declaredNames(new AstNode<>(loop, body.source())).contains("t"),
                "t must not be declared inside the loop block");
        assertTrue(declaredNames(body).contains("t"), "t must be declared in the method body");
    }

    @Test
    void reusedNameAcrossSiblingScopesGetsUniqueHoistedNames(@TempDir Path tmp) throws Exception {
        // Both `t`s are first stored inside a loop and read after it, so both
        // hoist to method level; emitting them under the shared source name
        // is a "Duplicate definition". Each gets a unique `t@N`, and the
        // loads and stores of each identity follow its new name.
        var t = translate(tmp, "Reuse", """
                public class Reuse {
                    static int g(int n) {
                        int a = 0;
                        {
                            int t;
                            int i = 0;
                            while (true) {
                                t = i;
                                i = i + 1;
                                if (i > n) {
                                    break;
                                }
                            }
                            a = a + t;
                        }
                        {
                            long t;
                            long j = 0;
                            while (true) {
                                t = j;
                                j = j + 1;
                                if (j > n) {
                                    break;
                                }
                            }
                            a = a + (int) t;
                        }
                        return a;
                    }
                }
                """);
        assertEquals(List.of(), t.messages());
        List<String> names = declaredNames(bodyOf(t.program(), "Reuse::g"));
        assertTrue(names.contains("t@1") && names.contains("t@2"),
                () -> "hoisted declarations must carry unique names, got " + names);
        assertEquals(0, names.stream().filter("t"::equals).count(),
                () -> "no declaration may keep the ambiguous name, got " + names);
        assertEquals(names.size(), names.stream().distinct().count(),
                () -> "duplicate declaration names: " + names);
    }

    // --- The back edge keeps a trailing if/else in tail position ---

    @Test
    void loopEndingInAnIfElseTranslates(@TempDir Path tmp) throws Exception {
        var t = translate(tmp, "Meet", """
                import static org.strata.jverify.JVerify.*;
                public class Meet {
                    static int meet(int len) {
                        precondition(len >= 0);
                        boolean go = true;
                        int fast = 0;
                        int steps = 0;
                        while (go) {
                            invariant(0 <= fast && fast <= len && steps == fast);
                            if (fast >= len) {
                                go = false;
                            } else {
                                fast = fast + 1;
                                steps = steps + 1;
                            }
                        }
                        return steps;
                    }
                }
                """);
        assertEquals(List.of(), t.messages());
    }

    @Test
    void loopEndingInATryTranslates(@TempDir Path tmp) throws Exception {
        var t = translate(tmp, "TryMeet", """
                import static org.strata.jverify.JVerify.*;
                public class TryMeet {
                    static int tf(int k) {
                        precondition(k == 1);
                        boolean go = true;
                        int steps = 0;
                        while (go) {
                            invariant(0 <= steps && steps <= 0 && k == 1);
                            try {
                                if (k == 1) {
                                    go = false;
                                } else {
                                    steps = steps + 1;
                                }
                            } finally {
                                k = 1;
                            }
                        }
                        return steps;
                    }
                }
                """);
        assertEquals(List.of(), t.messages());
    }

    // --- An early exit out of a try/finally leaves an inlined copy: refused ---

    /** The method translated without diagnostics. */
    private static void assertTranslated(Translation t, String proc) {
        assertEquals(List.of(), t.messages());
        bodyOf(t.program(), proc);
    }

    @Test
    void breakInsideATryFinallyInALoopTranslates(@TempDir Path tmp) throws Exception {
        // javac inlines one finally copy per normal exit; each is translated
        // where it is, so the finally runs once per iteration (EndToEndTest
        // checks the verdicts).
        var t = translate(tmp, "BreakFin", """
                import static org.strata.jverify.JVerify.*;
                public class BreakFin {
                    static int m() {
                        int i = 0;
                        int c = 0;
                        while (i < 3) {
                            try {
                                if (i == 10) {
                                    break;
                                }
                                i = i + 1;
                            } finally {
                                c = c + 1;
                            }
                        }
                        check(c == 6);
                        return c;
                    }
                }
                """);
        assertTranslated(t, "BreakFin::m");
    }

    @Test
    void invariantOverFinallyStateIsExtracted(@TempDir Path tmp) throws Exception {
        // The marker before the try is the loop's invariant (whether it holds is
        // checked end to end).
        var t = translate(tmp, "BreakFinInv", """
                import static org.strata.jverify.JVerify.*;
                public class BreakFinInv {
                    static int m() {
                        int i = 0;
                        int c = 0;
                        while (i < 3) {
                            invariant(c == 2 * i);
                            try {
                                if (i == 10) {
                                    break;
                                }
                                i = i + 1;
                            } finally {
                                c = c + 1;
                            }
                        }
                        return c;
                    }
                }
                """);
        assertLoopInvariants(t, "BreakFinInv::m", 1);
    }

    @Test
    void continueInsideATryFinallyInALoopTranslates(@TempDir Path tmp) throws Exception {
        // A continue out of the try compiles to a split protected range with
        // its own inlined copy, translated where it is.
        var t = translate(tmp, "ContFin", """
                import static org.strata.jverify.JVerify.*;
                public class ContFin {
                    static int m() {
                        int i = 0;
                        int c = 0;
                        while (i < 3) {
                            try {
                                if (i == 10) {
                                    continue;
                                }
                                i = i + 1;
                            } finally {
                                c = c + 1;
                            }
                        }
                        return c;
                    }
                }
                """);
        assertTranslated(t, "ContFin::m");
    }

    @Test
    void fallThroughTryFinallyInALoopStillTranslates(@TempDir Path tmp) throws Exception {
        // One normal exit, one inlined copy, fully stripped: the refusal
        // must not fire (see also backEdgeGoesInsideATrailingTry).
        var t = translate(tmp, "FallFin", """
                public class FallFin {
                    static int m() {
                        int i = 0;
                        int c = 0;
                        while (i < 3) {
                            try {
                                i = i + 1;
                            } finally {
                                c = c + 1;
                            }
                        }
                        return c;
                    }
                }
                """);
        assertEquals(List.of(), t.messages());
        bodyOf(t.program(), "FallFin::m");
    }

    @Test
    void breakAfterATryFinallyStillTranslates(@TempDir Path tmp) throws Exception {
        // The break is OUTSIDE the protected range, so the try still has the
        // single fall-through exit and its one copy is stripped.
        var t = translate(tmp, "BreakAfter", """
                public class BreakAfter {
                    static int m() {
                        int i = 0;
                        int c = 0;
                        while (i < 3) {
                            try {
                                i = i + 1;
                            } finally {
                                c = c + 1;
                            }
                            if (i >= 2) {
                                break;
                            }
                        }
                        return c;
                    }
                }
                """);
        assertEquals(List.of(), t.messages());
        bodyOf(t.program(), "BreakAfter::m");
    }



    // --- A leading break guard is part of the loop header ---

    private static final String BREAK_GUARD_WHILE = """
            import static org.strata.jverify.JVerify.*;
            public class GuardWhile {
                static int countTo(int n) {
                    precondition(0 <= n && n <= 1000);
                    int i = 0;
                    while (true) {
                        if (i >= n) {
                            break;
                        }
                        invariant(0 <= i && i <= n);
                        i = i + 1;
                    }
                    return i;
                }
            }
            """;

    @Test
    void invariantAfterALeadingBreakGuardIsExtracted(@TempDir Path tmp) throws Exception {
        // The old pre-tested-while recovery made the guard the loop
        // condition; without it the guard is a leading CRT statement and the
        // markers behind it must still be found.
        var t = translate(tmp, "GuardWhile", BREAK_GUARD_WHILE);
        assertLoopInvariants(t, "GuardWhile::countTo", 1);
    }

    @Test
    void invariantAfterALeadingBreakGuardInABareForIsExtracted(@TempDir Path tmp) throws Exception {
        var t = translate(tmp, "GuardFor", """
                import static org.strata.jverify.JVerify.*;
                public class GuardFor {
                    static int countTo(int n) {
                        precondition(0 <= n && n <= 1000);
                        int i = 0;
                        for (;;) {
                            if (i >= n) {
                                break;
                            }
                            invariant(0 <= i && i <= n);
                            i = i + 1;
                        }
                        return i;
                    }
                }
                """);
        assertLoopInvariants(t, "GuardFor::countTo", 1);
    }

    // --- The CRT-less tiers (no -Xjcov) fail closed ---

    /** {@code messages} minus the expected degraded-tier warning. */
    private static List<String> withoutTierWarning(Translation t) {
        return t.messages().stream().filter(m -> !m.contains("input compiled without")).toList();
    }

    @Test
    void markerAfterUserStatementIsRefusedWithoutCrt(@TempDir Path tmp) throws Exception {
        // Without CRT there is no evidence `i = i + 5` is condition lowering;
        // the old structural fallback skipped any assignment and promoted the
        // marker, which can verify a claim that is false where it was
        // written. Fail closed instead.
        var t = translateDebugTier(tmp, "AfterStmtG", """
                import static org.strata.jverify.JVerify.*;
                public class AfterStmtG {
                    static int m(int n) {
                        precondition(n >= 0 && n < 100);
                        int i = 0;
                        while (i < n) {
                            i = i + 5;
                            invariant(0 <= i && i <= n);
                            i = i - 4;
                        }
                        return i;
                    }
                }
                """);
        var messages = withoutTierWarning(t);
        assertEquals(1, messages.size(), () -> "messages: " + t.messages());
        assertTrue(messages.get(0).contains("call to contract method must come before the rest of the body"),
                () -> messages.get(0));
    }

    @Test
    void markerInsideAUserIfIsRefusedWithoutCrt(@TempDir Path tmp) throws Exception {
        // The old structural fallback descended into every test, promoting a
        // marker inside a user if.
        var t = translateDebugTier(tmp, "InsideIfG", """
                import static org.strata.jverify.JVerify.*;
                public class InsideIfG {
                    static int m(int n, boolean q) {
                        precondition(n >= 0 && n < 100);
                        int i = 0;
                        while (i < n) {
                            if (q) {
                                invariant(0 <= i && i <= n);
                            }
                            i = i + 1;
                        }
                        return i;
                    }
                }
                """);
        var messages = withoutTierWarning(t);
        assertEquals(1, messages.size(), () -> "messages: " + t.messages());
        assertTrue(messages.get(0).contains("call to contract method must come before the rest of the body"),
                () -> messages.get(0));
    }

    @Test
    void leadingBreakGuardInvariantIsExtractedWithoutCrt(@TempDir Path tmp) throws Exception {
        // The jump-only guard is recognizable without CRT (its arm holds
        // nothing but the loop's own exit), so this shape keeps working on
        // the -g-only tier.
        var t = translateDebugTier(tmp, "GuardWhileG", BREAK_GUARD_WHILE
                .replace("class GuardWhile", "class GuardWhileG"));
        assertEquals(List.of(), withoutTierWarning(t));
        assertEquals(1, loopBlock(bodyOf(t.program(), "GuardWhileG::countTo")).invariants().size());
    }
}
