package org.strata.jverify.langref.jls15.s15_17;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.3 -- Remainder Operator %: integer remainder follows the sign of the
// dividend, the identity (a/b)*b+(a%b)==a, and floating-point remainder.
class RemainderOperator_5 {
    static void run() {

        // The identity (a/b)*b + (a%b) == a
        int a = 17, b = 5;
        check((a / b) * b + (a % b) == a);
    }
}
