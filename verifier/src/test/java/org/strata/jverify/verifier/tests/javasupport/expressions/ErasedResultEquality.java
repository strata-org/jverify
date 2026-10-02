package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * Reference {@code ==} on the results of a generic call, where each result is a heap
 * composite.
 *
 * <p>The restricted-{@code ==} rule only rejects a comparison whose operands are BOTH
 * value-typed. Here both are {@code C}, a plain class, so the comparison is allowed.
 * A frontend that reads the operand types off the ERASED call descriptor sees
 * {@code Object} on both sides -- which it must assume may hold a value-translated
 * class -- and falsely rejects it. Verified 4 = {@code id} + {@code same} + the
 * implicit constructors of the fixture and C.
 */
@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_LIFTED_CALL_TYPE_INFERENCE}, methodsVerified = 4, errorCount = 0)
class ErasedResultEquality {
    static class C {
    }

    static <T> T id(T x) {
        return x;
    }

    static boolean same(C a, C b) {
        return id(a) == id(b);
    }
}
