package org.strata.jverify.langref.jls15.s15_22;

import static org.strata.jverify.JVerify.*;

// JLS 15.22.1 -- Integer Bitwise Operators & ^ |: bitwise AND, XOR, OR on int/long
class IntegerBitwiseOperators_10 {
    static void run() {

        // negative operands use two's complement
        check((-1 & 0x5) == 0x5);
    }
}
