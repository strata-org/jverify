package org.strata.jverify.langref.jls8.s8_9;

import static org.strata.jverify.JVerify.*;

// JLS 8.9.2 -- Enum Body Declarations: constant-specific class bodies overriding an abstract method.
class EnumBodyDeclarations_1 {
    enum Op {
        PLUS {
            @Override int apply(int a, int b) { return a + b; }
        },
        TIMES {
            @Override int apply(int a, int b) { return a * b; }
        };

        abstract int apply(int a, int b);
    }

    static void run() {

        check(Op.PLUS.apply(2, 3) == 5);
    }
}
