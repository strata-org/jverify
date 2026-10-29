package org.strata.jverify.langref.jls15.s15_17;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.3 -- Remainder Operator %: integer remainder follows the sign of the
// dividend, the identity (a/b)*b+(a%b)==a, and floating-point remainder.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_17.RemainderOperator_6#run")
class RemainderOperator_6 {
    static void run() {

        // Exact division yields zero remainder
        check(12 % 4 == 0);
    }
}
