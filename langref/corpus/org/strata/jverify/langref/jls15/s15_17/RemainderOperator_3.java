package org.strata.jverify.langref.jls15.s15_17;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.3 -- Remainder Operator %: integer remainder follows the sign of the
// dividend, the identity (a/b)*b+(a%b)==a, and floating-point remainder.
class RemainderOperator_3 {
    static void run() {

        check(7 % (-3) == 1);
    }
}
