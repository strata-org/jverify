package org.strata.jverify.langref.jls15.s15_22;

import static org.strata.jverify.JVerify.*;

// JLS 15.22.2 -- Boolean Logical Operators & ^ |: non-short-circuit logical AND, XOR, OR on boolean
class BooleanLogicalOperators_4 {
    static void run() {

        boolean t = true;
        boolean f = false;

        // logical OR (|): true iff either operand true
        check((t | f) == true);
    }

    static boolean sideEffectTrue() {
        return true;
    }

    static boolean sideEffectFalse() {
        return false;
    }
}
