package org.strata.jverify.langref.jls15.s15_22;

import static org.strata.jverify.JVerify.*;

// JLS 15.22.1 -- Integer Bitwise Operators & ^ |: bitwise AND, XOR, OR on int/long
class IntegerBitwiseOperators_1 {
    static void run() {

        int a = 0b1100;
        int b = 0b1010;

        // bitwise AND
        check((a & b) == 0b1000);
    }
}
