package org.strata.jverify.langref.jls14.s14_21;

import static org.strata.jverify.JVerify.*;

// JLS 14.21 -- The yield Statement: yields a value from a switch expression block.
class YieldStatement_2 {
    static void run() {

        int day = 3;

        // yield inside a colon-style switch expression
        int category = switch (day) {
            case 1:
            case 2:
                yield 10;
            case 3:
                yield 20;
            default:
                yield 0;
        };
        check(category == 20);
    }
}
