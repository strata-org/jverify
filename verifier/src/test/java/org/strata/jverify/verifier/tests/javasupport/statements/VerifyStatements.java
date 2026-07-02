package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@SuppressWarnings({"StatementWithEmptyBody", "ConstantValue"})
// Statement/expression coverage that verifies cleanly today. The loop methods
// that bearing invariants (or that depend on loop elimination to discharge a
// post-loop check) hit the Strata-side LoopElim limitation and live in the
// skipped companion VerifyStatementsLoops; see that file for the rationale.
//
// The @Pure P/P2/P3 chain and the methodWithResult/ignoreCallResult pair are
// static so their (static -> static) calls are not subject to the polymorphic-
// dispatch refusal that now applies to every non-final virtual call: a static
// callee can never be overridden, so static mangling resolves it soundly.
@JVerifyTest(methodsVerified = 9, methodsSkipped = 0, errorCount = 0, methodsInvalid = 0)
class VerifyStatements {
    void skip() {
        ;;;;
        check(true);
    }

    void nativeAssert() {
        // Do not use assert true since it is removed by javac
        int x = 1;
        assert (x>0);
    }

    void underscoreVariableName() {
        var _ = 3;
    }

    @Pure
    static boolean P3(int x, int y, int z) {
        return x > (y+z);
    }

    @Pure
    static boolean P2(int x, int y) {
        return P3(x,y,0);
    }

    @Pure
    static boolean P(int x) {
        return P2(x,10);
    }

    static int methodWithResult() {
        return 3;
    }

    static void ignoreCallResult() {
        methodWithResult();
    }
}
