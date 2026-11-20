package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.invariant;

/**
 * An object allocation inside a loop invariant is refused gracefully, as in a
 * method pre/postcondition: `new T()` in contract position lowers to a
 * heap-allocating block that Strata cannot lift out of an invariant expression,
 * so it would otherwise reach Core un-lowered. The refusal is reported here
 * because the enclosing method is static.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsVerified = 2, methodsSkipped = 1, errorCount = 0)
class NewInLoopInvariantRefusal {
    static class Box {}

    static void newInLoopInvariant() {
//              ^ error: Unsupported expression: NullLiteralExpr
        for (int i = 0; i < 5; i = i + 1) {
            invariant(new Box() != null);
        }
    }
}
