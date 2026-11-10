package org.strata.java.bytecode.verifier.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.Body;
import org.strata.jverify.laurel.LaurelProgram;
import org.strata.jverify.laurel.Procedure;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.verifier.SourceFile;
import org.strata.java.bytecode.frontend.BytecodeFrontend;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Laurel-level shape of short-circuit chains (no Strata). javac compiles
 * {@code (a && b) || rest} to jumps in which the block of {@code rest} is
 * reached both when {@code a} fails and when {@code b} fails. Translating each
 * arm of the diamond to its merge point would copy {@code rest} into both
 * arms, doubling the program with every disjunct; the frontend folds only
 * regions whose shared blocks are constants and emits any other shared block
 * once, as a labelled block the operands jump to. These tests pin that every
 * comparison of a chain appears once in the procedure body, and that shapes
 * which are not such chains keep the plain ternary.
 */
class ShortCircuitChainTranslationTest {

    private static Path libraryJar() {
        return org.strata.java.bytecode.verifier.VerifierDriver.specLibraryLocation();
    }

    private static LaurelProgram translate(Path tmp, String className, String source) throws Exception {
        Path classes = Files.createDirectories(tmp.resolve("classes-" + className));
        var file = new SourceFile(tmp.resolve(className + ".java"), source);
        var compiled = BytecodeFrontend.compile(List.of(file), List.of(libraryJar()), classes, Writer.nullWriter());
        assertTrue(compiled.success(), () -> "javac failed: " + compiled.diagnostics());
        var reporter = new Reporter();
        var contracts = LibraryContracts.of(LibraryContracts.objectContractSource(), List.of(), List.of(libraryJar()));
        var result = new BytecodeToLaurelCompiler(reporter, true, List.of(libraryJar()), false, false, contracts)
                .analyze(List.of(file.toUri()), BytecodeFrontend.readClasses(classes));
        assertEquals(List.of(), reporter.diagnostics().stream().map(d -> d.getMessage(null)).toList());
        return result.program();
    }

    /**
     * {@link #translate} compiled with {@code -g} only: no CharacterRangeTable,
     * the tier the frontend falls back to when {@code javac -Xjcov} fails.
     * Only the degraded-tier warning is expected.
     */
    private static LaurelProgram translateDebugTier(Path tmp, String className, String source) throws Exception {
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
        var reporter = new Reporter();
        var contracts = LibraryContracts.of(LibraryContracts.objectContractSource(), List.of(), List.of(libraryJar()));
        var result = new BytecodeToLaurelCompiler(reporter, true, List.of(libraryJar()), false, false, contracts)
                .analyze(List.of(file.toUri()), BytecodeFrontend.readClasses(classes));
        assertEquals(List.of(), reporter.diagnostics().stream().map(d -> d.getMessage(null))
                .filter(m -> !m.contains("input compiled without")).toList());
        return result.program();
    }

    /** The statements of the opaque implementation of static procedure {@code name}, rendered by the records' toString. */
    private static String body(LaurelProgram p, String name) {
        Procedure proc = p.staticProcedures().stream().filter(q -> q.name().text().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no procedure " + name));
        var opaque = assertInstanceOf(Body.Opaque.class, proc.body());
        var block = assertInstanceOf(StmtExpr.Block.class, opaque.implementation().orElseThrow().val());
        return block.statements().toString();
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    private static String literal(long value) {
        return "LiteralInt[value=" + value + "]";
    }

    /** {@code (c >= 0 && c <= 5) || (c >= 10 && c <= 15) || ...}, {@code n} disjuncts. */
    private static String ranges(int n) {
        return IntStream.range(0, n).mapToObj(i -> "(c >= " + 10 * i + " && c <= " + (10 * i + 5) + ")")
                .collect(Collectors.joining(" || "));
    }

    /** {@code (c != 0 || c != 5) && (c != 10 || c != 15) && ...}, {@code n} conjuncts. */
    private static String exclusions(int n) {
        return IntStream.range(0, n).mapToObj(i -> "(c != " + 10 * i + " || c != " + (10 * i + 5) + ")")
                .collect(Collectors.joining(" && "));
    }

    private static String wrap(String className, String methods) {
        return "class " + className + " {\n" + methods.indent(4) + "}\n";
    }

    @Test
    void everyRangeOfADisjunctionAppearsOnce(@TempDir Path tmp) throws Exception {
        int n = 12;
        var p = translate(tmp, "Ranges", wrap("Ranges", """
                static boolean ok(int c) {
                    return %s;
                }
                """.formatted(ranges(n))));
        String body = body(p, "Ranges::ok");
        for (int i = 0; i < n; i++) {
            assertEquals(1, count(body, literal(10 * i + 5)), "upper bound of disjunct " + i + " in " + body);
            if (i > 0) {
                assertEquals(1, count(body, literal(10 * i)), "lower bound of disjunct " + i + " in " + body);
            }
        }
    }

    @Test
    void everyExclusionOfAConjunctionAppearsOnce(@TempDir Path tmp) throws Exception {
        int n = 12;
        var p = translate(tmp, "Exclusions", wrap("Exclusions", """
                static boolean ok(int c) {
                    return %s;
                }
                """.formatted(exclusions(n))));
        String body = body(p, "Exclusions::ok");
        for (int i = 0; i < n; i++) {
            assertEquals(1, count(body, literal(10 * i + 5)), "second exclusion of conjunct " + i + " in " + body);
            if (i > 0) {
                assertEquals(1, count(body, literal(10 * i)), "first exclusion of conjunct " + i + " in " + body);
            }
        }
    }

    /**
     * A character class has hundreds of ranges. Both the value-diamond test
     * ({@code armIsValue}) and the translation must stay linear: before, each
     * doubled with every disjunct, and 200 never finished.
     */
    @Test
    void twoHundredRangesTranslateInBoundedTime(@TempDir Path tmp) throws Exception {
        int n = 200;
        String source = wrap("Wide", """
                static boolean ok(int c) {
                    return %s;
                }
                static boolean excluded(int c) {
                    return %s;
                }
                """.formatted(ranges(n), exclusions(n)));
        var p = assertTimeoutPreemptively(Duration.ofSeconds(30), () -> translate(tmp, "Wide", source));
        for (String name : List.of("Wide::ok", "Wide::excluded")) {
            String body = body(p, name);
            assertEquals(1, count(body, literal(10 * (n - 1) + 5)), name + ": " + body.length() + " chars");
            assertEquals(1, count(body, literal(10 * (n / 2))), name + ": " + body.length() + " chars");
        }
    }

    /** The translation's stack must not grow with the chain: 600 operands overflowed a recursive rest. */
    @Test
    void sixHundredRangesTranslateInBoundedTime(@TempDir Path tmp) throws Exception {
        int n = 600;
        String source = wrap("Wider", """
                static boolean ok(int c) {
                    return %s;
                }
                """.formatted(ranges(n)));
        var p = assertTimeoutPreemptively(Duration.ofSeconds(60), () -> translate(tmp, "Wider", source));
        String body = body(p, "Wider::ok");
        assertEquals(1, count(body, literal(10 * (n - 1) + 5)), body.length() + " chars");
    }

    /**
     * The chain is the condition of an int-valued ternary: the shared block's
     * value is an int, so the factored diamond must stay an {@code if}, not
     * become {@code chain && int}.
     */
    @Test
    void aChainDecidingAnIntTernaryKeepsTheIf(@TempDir Path tmp) throws Exception {
        var p = translate(tmp, "IntRest", wrap("IntRest", """
                static int thenInt(int a, int b, int c, int d) {
                    return ((a > 0 && b > 0) || c > 0) ? (d > 0 ? 2 : 1) : 0;
                }
                static int elseInt(int a, int b, int c, int d) {
                    return ((a > 0 && b > 0) || c > 0) ? 1 : (d > 0 ? 2 : 0);
                }
                """));
        String thenInt = body(p, "IntRest::thenInt");
        assertEquals(1, count(thenInt, literal(2)), thenInt);
        String elseInt = body(p, "IntRest::elseInt");
        assertEquals(1, count(elseInt, literal(2)), elseInt);
    }

    /**
     * In a switch-expression arm both sinks of the last operand end in
     * {@code goto}, so the operand is an int {@code if}: the factored value
     * must be an int {@code if} too, as it was before factoring.
     */
    @Test
    void aChainInASwitchArmStaysInt(@TempDir Path tmp) throws Exception {
        var p = translate(tmp, "SwitchArm", wrap("SwitchArm", """
                static int ok(int k, int a, int b, int c) {
                    int r = switch (k) {
                        case 1 -> ((a > 0 && b > 0) || c > 0) ? 1 : 0;
                        default -> 5;
                    };
                    return r;
                }
                """));
        String body = body(p, "SwitchArm::ok");
        assertEquals(0, count(body, "$orElse"), body);
        assertEquals(1, count(body, literal(5)), body);
    }

    /** The chain decides a ternary with values other than 0 and 1: one {@code if} over the whole chain. */
    @Test
    void aChainDecidingAValueTernaryAppearsOnce(@TempDir Path tmp) throws Exception {
        int n = 12;
        var p = translate(tmp, "Values", wrap("Values", """
                static int ok(int c) {
                    return %s ? 1001 : 2002;
                }
                """.formatted(ranges(n))));
        String body = body(p, "Values::ok");
        for (int i = 0; i < n; i++) {
            assertEquals(1, count(body, literal(10 * i + 5)), "upper bound of disjunct " + i + " in " + body);
        }
        assertEquals(1, count(body, literal(1001)), body);
        assertEquals(1, count(body, literal(2002)), body);
    }

    /** {@code (a && b) ? x : y}: y was reached from both failing operands and copied twice. */
    @Test
    void theElseValueOfAConjunctionAppearsOnce(@TempDir Path tmp) throws Exception {
        var p = translate(tmp, "ElseValue", wrap("ElseValue", """
                static int ok(int a, int b, int x, int y) {
                    return a > 3 && b > 4 ? x + 5 : y + 6;
                }
                """));
        String body = body(p, "ElseValue::ok");
        assertEquals(1, count(body, literal(6)), body);
        assertEquals(1, count(body, literal(5)), body);
    }

    /** The chain is the condition of an {@code if} statement: one {@code if} with the chain as its condition. */
    @Test
    void aChainAsAnIfConditionAppearsOnce(@TempDir Path tmp) throws Exception {
        int n = 12;
        var p = translate(tmp, "Statement", wrap("Statement", """
                static int ifElse(int c) {
                    if (%1$s) {
                        return 1001;
                    } else {
                        return 2002;
                    }
                }
                static int ifOnly(int c) {
                    if (%1$s) {
                        return 1001;
                    }
                    return 2002;
                }
                static int guard(int c) {
                    if (!(%1$s)) {
                        c = 0;
                    }
                    return c;
                }
                """.formatted(ranges(n))));
        for (String name : List.of("Statement::ifElse", "Statement::ifOnly", "Statement::guard")) {
            String body = body(p, name);
            for (int i = 0; i < n; i++) {
                assertEquals(1, count(body, literal(10 * i + 5)), name + ": upper bound of disjunct " + i + " in " + body);
            }
        }
    }

    /**
     * Operands after the first are evaluated only when the earlier ones allow
     * it, so their implicit exceptions must stay inside the expression, not be
     * hoisted in front of the {@code if} like the first operand's.
     */
    @Test
    void laterOperandsOfAnIfChainKeepTheirGuards(@TempDir Path tmp) throws Exception {
        var p = translate(tmp, "Guarded", wrap("Guarded", """
                static int ok(int[] a, int i) {
                    if (i >= 0 && i < a.length && a[i] > 0) {
                        return a[i];
                    }
                    return -1;
                }
                """));
        String body = body(p, "Guarded::ok");
        assertEquals(2, count(body, "array_load"), body);
    }

    /** Statements of the if's body are not operands: an {@code assert} alone in the body stays an assert. */
    @Test
    void anAssertInAnIfBodyStaysAStatement(@TempDir Path tmp) throws Exception {
        var p = translate(tmp, "Asserting", wrap("Asserting", """
                static int ok(int a, int b) {
                    if (a > 0) {
                        assert b > 0;
                    }
                    return a;
                }
                """));
        String body = body(p, "Asserting::ok");
        assertEquals(1, count(body, "Assert["), body);
        assertEquals(1, count(body, "IfThenElse"), body);
        assertEquals(0, count(body, "$andThen"), body);
    }

    /** Nested ifs and a statement before an inner if are statements, not operands of the outer condition. */
    @Test
    void nestedIfsStayNested(@TempDir Path tmp) throws Exception {
        var p = translate(tmp, "Nesting", wrap("Nesting", """
                static int plain(int a, int b) {
                    int r = 0;
                    if (a > 0) {
                        if (b > 0) {
                            r = 1;
                        }
                    }
                    return r;
                }
                static int bump(int a, int b) {
                    int r = 0;
                    if (a > 0) {
                        r = r + 2;
                        if (b > 0) {
                            r = 1;
                        }
                    }
                    return r;
                }
                static int ladder(int a, int b) {
                    int r;
                    if (a > 0) {
                        r = 1;
                    } else if (b > 0) {
                        r = 2;
                    } else {
                        r = 3;
                    }
                    return r;
                }
                """));
        for (String name : List.of("Nesting::plain", "Nesting::bump", "Nesting::ladder")) {
            String body = body(p, name);
            assertEquals(2, count(body, "IfThenElse"), name + ": " + body);
            assertEquals(0, count(body, "$andThen"), name + ": " + body);
            assertEquals(0, count(body, "$orElse"), name + ": " + body);
        }
    }

    /**
     * A division in a later operand throws ArithmeticException implicitly. When an enclosing catch (or
     * the throws clause) makes that throw explicit, it is emitted as a statement in front of the
     * operand's own {@code if}, so the operands keep their ifs; without one, the chain is one expression.
     */
    @Test
    void aLaterOperandWhoseExceptionIsCaughtKeepsItsOwnIf(@TempDir Path tmp) throws Exception {
        var p = translate(tmp, "Dividing", wrap("Dividing", """
                static int caught(int x, int y) {
                    int r = 0;
                    try {
                        if (x > 0 && x / y > 2) {
                            r = 1;
                        }
                    } catch (ArithmeticException e) {
                        r = -1;
                    }
                    return r;
                }
                static int undeclared(int x, int y) {
                    int r = 0;
                    if (x > 0 && x / y > 2) {
                        r = 1;
                    }
                    return r;
                }
                static int declaredOther(int x, int y) throws java.io.IOException {
                    int r = 0;
                    if (x > 0 && x / y > 2) {
                        r = 1;
                    }
                    return r;
                }
                """));
        // An enclosing catch, or no throws clause at all, makes the throw explicit: operands keep their ifs.
        for (String name : List.of("Dividing::caught", "Dividing::undeclared")) {
            String body = body(p, name);
            assertEquals(1, count(body, "Throw["), name + ": " + body);
            assertEquals(0, count(body, "$andThen"), name + ": " + body);
        }
        String declaredOther = body(p, "Dividing::declaredOther");
        assertEquals(0, count(declaredOther, "Throw["), declaredOther);
    }

    /**
     * Shapes with no block shared by both arms, with the method ending inside
     * the if, and with a ternary on each side, at 600 operands: the loop, not
     * recursion per operand, must carry them all.
     */
    @Test
    void otherLongChainShapesTranslateInBoundedTime(@TempDir Path tmp) throws Exception {
        int n = 600;
        String conjunction = IntStream.range(0, n).mapToObj(i -> "c != " + 10 * i).collect(Collectors.joining(" && "));
        String disjunction = IntStream.range(0, n).mapToObj(i -> "c == " + 10 * i).collect(Collectors.joining(" || "));
        String source = wrap("Shapes", """
                static int conjunctionIf(int c) {
                    if (%1$s) {
                        return 1001;
                    }
                    return 2002;
                }
                static int disjunctionReturns(int c) {
                    if (%2$s) {
                        return 1001;
                    }
                    return 2002;
                }
                static int twoTernaries(int c, int d) {
                    return (%2$s) ? (d > 0 ? 1001 : 2002) : (d > 1 ? 3003 : 4004);
                }
                """.formatted(conjunction, disjunction));
        var p = assertTimeoutPreemptively(Duration.ofSeconds(60), () -> translate(tmp, "Shapes", source));
        for (String name : List.of("Shapes::conjunctionIf", "Shapes::disjunctionReturns", "Shapes::twoTernaries")) {
            String body = body(p, name);
            assertEquals(1, count(body, literal(10 * (n - 1))), name + ": " + body.length() + " chars");
            assertEquals(1, count(body, literal(1001)), name + ": " + body.length() + " chars");
            assertEquals(1, count(body, literal(2002)), name + ": " + body.length() + " chars");
        }
    }

    /**
     * An array allocation inside a condition expression is refused, so an
     * operand that allocates keeps its own {@code if}, where the allocation
     * is a statement in front of it, as before.
     */
    @Test
    void anOperandThatAllocatesAnArrayKeepsItsOwnIf(@TempDir Path tmp) throws Exception {
        var p = translate(tmp, "Allocating", wrap("Allocating", """
                static int ok(int n) {
                    int r = 0;
                    if (n > 0 && new int[n].length == n) {
                        r = 1;
                    }
                    return r;
                }
                """));
        String body = body(p, "Allocating::ok");
        assertEquals(2, count(body, "IfThenElse"), body);
        assertEquals(0, count(body, "$andThen"), body);
    }

    /**
     * Without a CharacterRangeTable, statement boundaries come from the
     * LineNumberTable and the block's shape: a chain on one line still merges,
     * while a nested {@code if}, an {@code assert} in the body and a statement
     * before an inner {@code if} stay statements.
     */
    @Test
    void withoutACharacterRangeTableStatementsStayStatements(@TempDir Path tmp) throws Exception {
        int n = 12;
        var p = translateDebugTier(tmp, "NoCrt", wrap("NoCrt", """
                static int chain(int c) {
                    if (%s) {
                        return 1001;
                    }
                    return 2002;
                }
                static int nested(int a, int b) {
                    int r = 0;
                    if (a > 0) {
                        if (b > 0) {
                            r = 1;
                        }
                    }
                    return r;
                }
                static int asserting(int a, int b) {
                    if (a > 0) {
                        assert b > 0;
                    }
                    return a;
                }
                static int bump(int a, int b) {
                    int r = 0;
                    if (a > 0) {
                        r++;
                        if (b > 0) {
                            r = 1;
                        }
                    }
                    return r;
                }
                """.formatted(ranges(n))));
        String chain = body(p, "NoCrt::chain");
        assertEquals(1, count(chain, literal(10 * (n - 1) + 5)), chain);
        String nested = body(p, "NoCrt::nested");
        assertEquals(2, count(nested, "IfThenElse"), nested);
        assertEquals(0, count(nested, "$andThen"), nested);
        String asserting = body(p, "NoCrt::asserting");
        assertEquals(1, count(asserting, "Assert["), asserting);
        assertEquals(1, count(asserting, "IfThenElse"), asserting);
        String bump = body(p, "NoCrt::bump");
        assertEquals(2, count(bump, "IfThenElse"), bump);
        assertEquals(0, count(bump, "$andThen"), bump);
    }

    /** A chain whose shared block lies between two inner shared blocks: factoring applies at every level. */
    @Test
    void nestedChainsAppearOnce(@TempDir Path tmp) throws Exception {
        var p = translate(tmp, "Nested", wrap("Nested", """
                static boolean ok(int c) {
                    return ((c >= 0 && c <= 5) || (c >= 10 && c <= 15)) && c != 3 && c != 12;
                }
                """));
        String body = body(p, "Nested::ok");
        for (long v : new long[] {5, 10, 15, 3, 12}) {
            assertEquals(1, count(body, literal(v)), "literal " + v + " in " + body);
        }
    }

    /** The chain as a call argument closes at the call, not at the return: the same factoring applies. */
    @Test
    void aChainPassedAsAnArgumentAppearsOnce(@TempDir Path tmp) throws Exception {
        int n = 12;
        var p = translate(tmp, "Argument", wrap("Argument", """
                static boolean id(boolean x) {
                    return x;
                }
                static boolean ok(int c) {
                    return id(%s);
                }
                """.formatted(ranges(n))));
        String body = body(p, "Argument::ok");
        for (int i = 0; i < n; i++) {
            assertEquals(1, count(body, literal(10 * i + 5)), "upper bound of disjunct " + i + " in " + body);
        }
    }

    /**
     * The same comparison in both arms of a ternary is two blocks in bytecode
     * (javac does not share them), so nothing is factored and each arm keeps
     * its copy. A block really reached with the chain {@code true} on one arm
     * and {@code false} on the other cannot be written in Java; that case is
     * the fallback to the plain ternary in {@code diamondValue}.
     */
    @Test
    void aComparisonRepeatedInBothArmsStaysInEachArm(@TempDir Path tmp) throws Exception {
        var p = translate(tmp, "Mixed", wrap("Mixed", """
                static boolean ok(boolean a, boolean b, boolean d, int c) {
                    return a ? (b || c > 7) : (d && c > 7);
                }
                """));
        String body = body(p, "Mixed::ok");
        assertEquals(2, count(body, literal(7)), body);
    }

    /** A value ternary has no shared condition block: unchanged. */
    @Test
    void aValueTernaryIsAnIfThenElse(@TempDir Path tmp) throws Exception {
        var p = translate(tmp, "Ternary", wrap("Ternary", """
                static int ok(int c) {
                    return c > 7 ? c : 7 - c;
                }
                """));
        String body = body(p, "Ternary::ok");
        assertEquals(1, count(body, "IfThenElse"), body);
    }

    /** Both arms are literals of opposite value: the ternary is the condition itself, not {@code cond || false}. */
    @Test
    void aTernaryOfOppositeLiteralsIsTheCondition(@TempDir Path tmp) throws Exception {
        var p = translate(tmp, "Literals", wrap("Literals", """
                static boolean ok(int c) {
                    return c > 7 ? true : false;
                }
                static boolean negated(int c) {
                    return c > 7 ? false : true;
                }
                """));
        String ok = body(p, "Literals::ok");
        assertEquals(0, count(ok, "LiteralBool"), ok);
        assertEquals(1, count(ok, literal(7)), ok);
        String negated = body(p, "Literals::negated");
        assertEquals(0, count(negated, "LiteralBool"), negated);
        assertEquals(1, count(negated, literal(7)), negated);
    }
}
