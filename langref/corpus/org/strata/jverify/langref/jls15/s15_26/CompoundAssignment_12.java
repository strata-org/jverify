package org.strata.jverify.langref.jls15.s15_26;

import static org.strata.jverify.JVerify.*;

// JLS 15.26.2 -- Compound Assignment Operators (op=): E1 op= E2 is equivalent
// to E1 = (T)((E1) op (E2)) where T is the type of E1, with E1 evaluated once
// and an implicit narrowing cast applied to the result.
class CompoundAssignment_12 {
    static void run() {

        // Implicit narrowing cast: byte b = (byte)(b + 1) — overflow wraps to -128.
        byte b = 127;
        b += 1;
        check(b == -128);
    }
}
