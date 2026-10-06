package org.strata.jverify.langref.jls15.s15_22;

import static org.strata.jverify.JVerify.*;

// JLS 15.22.1 -- Integer Bitwise Operators & ^ |: bitwise AND, XOR, OR on int/long
class IntegerBitwiseOperators_12 {
    static void run() {

        check((-1 ^ -1) == 0);
    }
}
