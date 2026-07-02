package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.invariant;

/**
 * An object allocation inside a loop invariant is refused gracefully, the same
 * way it is in a method pre/postcondition: a `new T()` in contract position
 * lowers to a heap-allocating block that Strata cannot lift out of an invariant
 * expression, so it would otherwise reach Core as an un-lowered block. The
 * refusal is reported here because the enclosing method is static.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsVerified = 2, methodsSkipped = 1, errorCount = 0)
class NewInLoopInvariantRefusal {
    static class Box {}

    static void newInLoopInvariant() {
//              ^ error: object allocation (including lambdas) inside a contract is not yet supported
        for (int i = 0; i < 5; i = i + 1) {
            invariant(new Box() != null);
        }
    }
}
