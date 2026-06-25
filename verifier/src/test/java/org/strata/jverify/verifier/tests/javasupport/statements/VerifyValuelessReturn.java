package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/** Valueless {@code return;} (#443): once dropped as {@code null}, so it crashed or lost early-exit semantics. */
@SuppressWarnings({"ConstantValue"})
@JVerifyTest(exitCode = 0, methodsVerified = 6, errorCount = 0)
class VerifyValuelessReturn {

    /** Crash case + soundness probe: a dropped return makes check(false) reachable. */
    static void ifThenBareReturn() {
        int x = 5;
        if (x == 5) return;
        check(false);
    }

    /** Crash case: labeled bare return. */
    static void labeledBareReturn() {
        int x = 5;
        lbl: return;
    }

    /** Crash case: bare return inside a loop. */
    static void bareReturnInLoop() {
        int x = 0;
        do {
            invariant(0 <= x && x <= 2);
            if (x == 2) return;
            x = x + 1;
        } while (x < 3);
    }

    /** The early return must guard the rest: dropping it makes check(x > 0) reachable with x <= 0. */
    static void earlyReturnGuardsRest(int x) {
        if (x <= 0) return;
        check(x > 0);
    }

    /** Trailing bare return: must still verify. */
    static void trailingBareReturn() {
        int x = 1;
        check(x == 1);
        return;
    }
}
