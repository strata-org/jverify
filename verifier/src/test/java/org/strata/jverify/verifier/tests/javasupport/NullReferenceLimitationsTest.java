package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

/**
 * Deliberate limitations of the stop-gap per-sort {@code <sort>$null} null
 * model (see {@code JavaToLaurelCompiler.NULL_REF_SUFFIX}). These are the cases
 * the eventual Laurel nullable model is meant to handle; pinning them as
 * should-fail shows the current model is neither unsound nor over-permissive:
 * it treats an unconstrained reference as possibly-{@code null} and
 * possibly-non-{@code null}, and cannot invent non-nullness.
 *
 * <p>Companion to {@code NullReferenceTest}, which covers what <em>does</em>
 * work.
 */
@JVerifyTest(exitCode = 4, methodsVerified = 1, errorCount = 3)
class NullReferenceLimitationsTest {

    /**
     * Not over-permissive: an unconstrained reference cannot be proved
     * non-null — it may be the sort's {@code $null} element.
     */
    static void cannotProveParamNonNull(Object o) {
        check(o != null);
//      ^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    /**
     * ...and, symmetrically, cannot be proved null. The model is agnostic
     * about an unconstrained reference, not biased either way.
     */
    static void cannotProveParamNull(Object o) {
        check(o == null);
//      ^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    /**
     * A freshly allocated object is not provably non-null: {@code new T(...)}
     * lowers to an opaque {@code new_(T)} value carrying no fact that it
     * differs from {@code T$null}.
     */
    static void cannotProveFreshObjectNonNull() {
        Object o = new Object();
        check(o != null);
//      ^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
