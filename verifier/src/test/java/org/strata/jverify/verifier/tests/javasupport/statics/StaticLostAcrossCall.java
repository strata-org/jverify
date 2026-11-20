package org.strata.jverify.verifier.tests.javasupport.statics;

import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * After a call, the caller knows about a static field the callee writes only
 * what the callee's contract says. A failed assertion about the field names
 * the call. A {@code @Transparent} callee is seen through, and a callee that
 * does not write the field leaves it as it was.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 4, errorCount = 1)
class StaticLostAcrossCall {

    static int counter;
    static int other;

    static void bump() {
        precondition(counter < 100);
        counter++;
    }

    static void bumpOther() {
        precondition(other < 100);
        other++;
    }

    @Transparent
    static int same(int x) {
        return x;
    }

    static void valueLostAcrossCall() {
        counter = 5;
        bump();
        check(counter == 6);
//      ^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold; a static field may have been changed by the call to 'bump()', whose body is not visible to the caller
    }

    static void unrelatedWriterKeepsIt() {
        counter = 5;
        other = 0;
        bumpOther();
        check(counter == 5);
    }

    static void transparentCalleeKeepsIt() {
        counter = 5;
        int t = same(counter);
        check(counter == 5 && t == 5);
    }
}
