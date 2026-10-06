package org.strata.jverify.langref.jls15.s15_22;

import static org.strata.jverify.JVerify.*;

// JLS 15.22.2 -- Boolean Logical Operators & ^ |: non-short-circuit logical AND, XOR, OR on boolean
class BooleanLogicalOperators_9 {
    static void run() {

        boolean f = false;

        // unlike && and ||, & and | always evaluate both operands (no short-circuit)
        check((f & sideEffectTrue()) == false);
    }

    static boolean sideEffectTrue() {
        return true;
    }

    static boolean sideEffectFalse() {
        return false;
    }
}
