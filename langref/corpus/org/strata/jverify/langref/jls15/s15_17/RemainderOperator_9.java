package org.strata.jverify.langref.jls15.s15_17;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.3 -- Remainder Operator %: integer remainder follows the sign of the
// dividend, the identity (a/b)*b+(a%b)==a, and floating-point remainder.
class RemainderOperator_9 {
    static void run() {

        // floating-point remainder
        double d = 7.5 % 2.0;
        check(d == 1.5);
    }
}
