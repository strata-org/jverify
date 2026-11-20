package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A local declared at the top of a {@code while (a || b)} body: the
 * short-circuit condition duplicates the body into the arm of each test, but
 * the local's declaration is emitted only in the first copy, so the second
 * copy's references fail to resolve ("'t' is not defined"). The same break
 * hits a counter snapshot ({@code final int i0 = i;}) under an {@code ||}
 * condition. No contract calls are needed to reproduce it.
 */
@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER}, exitCode = 0)
class OrLoopLeadingLocal {
    static int orCondition(int n, int m) {
        precondition(0 <= n && n <= 1000);
        precondition(0 <= m && m <= 1000);
        int i = 0;
        while (i < n || i < m) {
            int t = i;
            i = t + 1;
        }
        return i;
    }
}
