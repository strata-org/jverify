package org.strata.jverify.contracts2jqwik;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests for the source-to-source translator.
 *
 * <p>Each test parses a tiny Java snippet representing an {@code @AsProperty}
 * method, runs the translator, and asserts on the output. We deliberately
 * stay at the source-text level — comparing exact strings would be brittle
 * across JavaParser versions, so we assert on substrings that capture the
 * meaningful properties of the translation.</p>
 */
class Contracts2JqwikTest {

    private static String translate(String source) {
        Contracts2Jqwik.Result r = Contracts2Jqwik.translate(source, "Test.java");
        assertNotNull(r.output, "expected non-null output for source:\n" + source
            + "\nwarnings: " + r.warnings);
        return r.output;
    }

    @Test
    void simpleSinglePostcondition() {
        String source = """
            import org.strata.jverify.AsProperty;
            import net.jqwik.api.ForAll;
            import static org.strata.jverify.JVerify.*;
            class C {
                @AsProperty
                int doubleIt(@ForAll int x) {
                    postcondition((Integer r) -> r == 2 * x);
                    throw new RuntimeException();
                }
            }
            """;
        String out = translate(source);

        assertTrue(out.contains("class CGenerated extends C"),
            "expected CGenerated extends C, got:\n" + out);
        assertTrue(out.contains("@Property"), "expected @Property annotation");
        assertTrue(out.contains("doubleItProperty"),
            "expected method named doubleItProperty");
        assertTrue(out.contains("@ForAll int x"),
            "expected @ForAll preserved on parameter");
        assertTrue(out.contains("int r = doubleIt(x);"),
            "expected call binding to original method");
        assertTrue(out.contains("return r == 2 * x;"),
            "expected verbatim return of postcondition body");
    }

    @Test
    void multiplePostconditionsAreConjoined() {
        String source = """
            import org.strata.jverify.AsProperty;
            import net.jqwik.api.ForAll;
            class C {
                @AsProperty
                int abs(@ForAll int x) {
                    postcondition((Integer r) -> r >= 0);
                    postcondition((Integer r) -> r == x || r == -x);
                    throw new RuntimeException();
                }
            }
            """;
        String out = translate(source);

        // Both conjuncts present and joined with && in source order.
        assertTrue(out.contains("r >= 0"), "first postcondition body missing");
        assertTrue(out.contains("r == x || r == -x"),
            "second postcondition body missing");
        // The && operator appears between the two clauses
        int firstEnd = out.indexOf("r >= 0") + "r >= 0".length();
        int secondStart = out.indexOf("r == x || r == -x");
        assertTrue(firstEnd > 0 && secondStart > firstEnd,
            "expected first then second clause in source order");
        assertTrue(out.substring(firstEnd, secondStart).contains("&&"),
            "expected && between the two clauses");
    }

    @Test
    void multiplePreconditionsEmitOneAssumeEach() {
        String source = """
            import org.strata.jverify.AsProperty;
            import net.jqwik.api.ForAll;
            import static org.strata.jverify.JVerify.*;
            class C {
                @AsProperty
                int divide(@ForAll int a, @ForAll int b) {
                    precondition(b != 0);
                    precondition(a >= 0);
                    postcondition((Integer r) -> r * b == a || true);
                    throw new RuntimeException();
                }
            }
            """;
        String out = translate(source);

        assertTrue(out.contains("Assume.that(b != 0)"),
            "expected first precondition translated to Assume.that");
        assertTrue(out.contains("Assume.that(a >= 0)"),
            "expected second precondition translated to Assume.that");
    }

    @Test
    void disagreeingBindingNamesAreRejected() {
        String source = """
            import org.strata.jverify.AsProperty;
            import net.jqwik.api.ForAll;
            class C {
                @AsProperty
                int abs(@ForAll int x) {
                    postcondition((Integer r) -> r >= 0);
                    postcondition((Integer s) -> s == x || s == -x);
                    throw new RuntimeException();
                }
            }
            """;
        Contracts2Jqwik.Result result = Contracts2Jqwik.translate(source, "Test.java");
        // No method should be emitted — the class becomes empty so no class is
        // emitted either.
        assertNull(result.output, "expected null output when binding names disagree");
        assertTrue(result.warnings.stream().anyMatch(w ->
                w.contains("different return-binding names")),
            "expected warning about disagreeing binding names; got: " + result.warnings);
    }

    @Test
    void quantifiersAreSkippedWithWarning() {
        String source = """
            import org.strata.jverify.AsProperty;
            import net.jqwik.api.ForAll;
            import static org.strata.jverify.JVerify.*;
            class C {
                @AsProperty
                int sumPositive(@ForAll int[] xs) {
                    precondition(forall((Integer i) -> i >= 0));
                    postcondition((Integer r) -> r >= 0);
                    throw new RuntimeException();
                }
            }
            """;
        Contracts2Jqwik.Result result = Contracts2Jqwik.translate(source, "Test.java");
        assertNotNull(result.output);
        // Quantifier precondition is skipped; postcondition still translated.
        assertFalse(result.output.contains("Assume.that(forall("),
            "quantifier precondition should not be emitted");
        assertTrue(result.output.contains("return r >= 0"),
            "executable postcondition should still be emitted");
        assertTrue(result.warnings.stream().anyMatch(w -> w.contains("non-executable")),
            "expected warning about non-executable clause; got: " + result.warnings);
    }

    @Test
    void noAsPropertyMethodsReturnsNullOutput() {
        String source = """
            class C {
                int plain(int x) { return x + 1; }
            }
            """;
        Contracts2Jqwik.Result result = Contracts2Jqwik.translate(source, "Test.java");
        assertNull(result.output, "expected null output when no @AsProperty present");
    }

    @Test
    void postconditionWithoutLambdaDoesNotBindResult() {
        String source = """
            import org.strata.jverify.AsProperty;
            import net.jqwik.api.ForAll;
            class C {
                @AsProperty
                boolean isEven(@ForAll int x) {
                    postcondition(x % 2 == 0 || x % 2 == 1);
                    throw new RuntimeException();
                }
            }
            """;
        String out = translate(source);
        // No `T r =` line because no clause introduced a binding.
        assertFalse(out.contains("= isEven("),
            "expected no return-binding when no lambda clause exists; got:\n" + out);
        // The call still happens as a side-effecting statement, then we return.
        assertTrue(out.contains("isEven(x);"),
            "expected unbound call to original method; got:\n" + out);
        assertTrue(out.contains("return x % 2 == 0 || x % 2 == 1"),
            "expected verbatim postcondition body in return; got:\n" + out);
    }

    @Test
    void packageAndImportsAreCarriedForward() {
        String source = """
            package com.example.demo;
            import org.strata.jverify.AsProperty;
            import net.jqwik.api.ForAll;
            import java.util.List;
            class C {
                @AsProperty
                int identity(@ForAll int x) {
                    postcondition((Integer r) -> r == x);
                    throw new RuntimeException();
                }
            }
            """;
        String out = translate(source);
        assertTrue(out.contains("package com.example.demo"),
            "expected package preserved; got:\n" + out);
        assertTrue(out.contains("import java.util.List"),
            "expected user imports preserved; got:\n" + out);
    }
}
