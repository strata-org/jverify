package org.strata.jverify.langref.jls15.s15_22;

import static org.strata.jverify.JVerify.*;

// JLS 15.22.1 -- Integer Bitwise Operators & ^ |: bitwise AND, XOR, OR on int/long
class IntegerBitwiseOperators_9 {
    static void run() {

        // long operands
        long x = 0xF0F0L;
        long y = 0x0FF0L;
        check((x ^ y) == 0xFF00L);
    }
}
