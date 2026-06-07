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
        assertTrue(out.contains("return (r == 2 * x);"),
            "expected verbatim postcondition body wrapped in parens; got:\n" + out);
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
        // Each clause is wrapped in parens so Java's && / || precedence does
        // not silently change the meaning of the conjunction. Without parens
        // `r >= 0 && r == x || r == -x` parses as `(r >= 0 && r == x) || r == -x`.
        assertTrue(out.contains("(r >= 0)") && out.contains("(r == x || r == -x)"),
            "expected each clause body wrapped in parens; got:\n" + out);
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
        assertTrue(result.output.contains("return") && result.output.contains("r >= 0"),
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
        assertTrue(out.contains("return") && out.contains("x % 2 == 0 || x % 2 == 1"),
            "expected verbatim postcondition body in return; got:\n" + out);
    }

    @Test
    void oldOverParameterIsHoisted() {
        String source = """
            import org.strata.jverify.AsProperty;
            import net.jqwik.api.ForAll;
            import static org.strata.jverify.JVerify.*;
            class C {
                @AsProperty
                public static int increment(@ForAll int n) {
                    postcondition((Integer r) -> r == old(n) + 1);
                    return n + 1;
                }
            }
            """;
        String out = translate(source);
        // old(n) hoisted into a pre-call temp, referenced in the postcondition.
        assertTrue(out.contains("var __old0 = n;"),
            "expected old(n) hoisted to a pre-call temp; got:\n" + out);
        assertTrue(out.contains("int r = increment(n);"),
            "expected call binding after the capture; got:\n" + out);
        assertTrue(out.contains("__old0 + 1"),
            "expected old(n) replaced by the temp in the postcondition; got:\n" + out);
        assertFalse(out.contains("old("),
            "expected no residual old(...) call in the output; got:\n" + out);
    }

    @Test
    void frameClausesAreDropped() {
        String source = """
            import org.strata.jverify.AsProperty;
            import net.jqwik.api.ForAll;
            import static org.strata.jverify.JVerify.*;
            class C {
                @AsProperty
                public static int identity(@ForAll int n) {
                    reads(everything());
                    postcondition((Integer r) -> r == n);
                    return n;
                }
            }
            """;
        String out = translate(source);
        // reads(...) is a verifier-only frame clause; it must not appear in the
        // generated test and must not break translation.
        assertFalse(out.contains("reads("),
            "expected reads(...) frame clause dropped; got:\n" + out);
        assertTrue(out.contains("return (r == n)"),
            "expected the postcondition to still translate; got:\n" + out);
    }

    @Test
    void oldOverContractLocalIsSkipped() {
        // old() referencing a local declared inside the contract body (not a
        // parameter) cannot be captured in the generated test, so the clause
        // is skipped with a warning rather than emitting broken code.
        String source = """
            import org.strata.jverify.AsProperty;
            import net.jqwik.api.ForAll;
            import static org.strata.jverify.JVerify.*;
            class C {
                @AsProperty
                public static int appendCharLength(@ForAll char c) {
                    StringBuilder sb = new StringBuilder("seed");
                    modifies(sb);
                    postcondition((Integer r) -> r == old(sb.length()) + 1);
                    sb.append(c);
                    return sb.length();
                }
            }
            """;
        Contracts2Jqwik.Result result = Contracts2Jqwik.translate(source, "Test.java");
        // No translatable postcondition remains, so no output.
        assertNull(result.output,
            "expected null output when the only postcondition references a contract-body local");
        assertTrue(result.warnings.stream().anyMatch(w -> w.contains("not in scope")),
            "expected a clear out-of-scope warning; got: " + result.warnings);
    }
}
