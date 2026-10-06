package org.strata.jverify.langref.jls15.s15_17;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.3 -- Remainder Operator %: integer remainder follows the sign of the
// dividend, the identity (a/b)*b+(a%b)==a, and floating-point remainder.
class RemainderOperator_7 {
    static void run() {

        // long remainder
        long l = 1000000000007L % 1000L;
        check(l == 7L);
    }
}
