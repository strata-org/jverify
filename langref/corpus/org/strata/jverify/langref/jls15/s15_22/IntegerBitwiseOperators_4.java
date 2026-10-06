package org.strata.jverify.langref.jls15.s15_22;

import static org.strata.jverify.JVerify.*;

// JLS 15.22.1 -- Integer Bitwise Operators & ^ |: bitwise AND, XOR, OR on int/long
class IntegerBitwiseOperators_4 {
    static void run() {

        int a = 0b1100;

        // identity: a ^ a == 0, a | 0 == a, a & 0 == 0
        check((a ^ a) == 0);
    }
}
