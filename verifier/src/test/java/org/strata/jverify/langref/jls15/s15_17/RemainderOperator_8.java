package org.strata.jverify.langref.jls15.s15_17;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.3 -- Remainder Operator %: integer remainder follows the sign of the
// dividend, the identity (a/b)*b+(a%b)==a, and floating-point remainder.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_17.RemainderOperator_8#run")
class RemainderOperator_8 {
    static void run() {

        // Overflow special case: MIN_VALUE % -1 is 0 (no exception)
        int min = -2147483648;
        check(min % (-1) == 0);
    }
}
