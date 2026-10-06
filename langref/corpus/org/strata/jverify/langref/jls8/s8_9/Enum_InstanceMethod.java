package org.strata.jverify.langref.jls8.s8_9;

import static org.strata.jverify.JVerify.*;

// JLS 8.9.2 -- an enum body may declare instance methods; each constant
// inherits them and may call the implicit ordinal()/name().
class Enum_InstanceMethod {
    enum E {
        A, B;

        int who() {
            return ordinal() + 1;
        }
    }

    static void run() {
        check(E.B.who() == 2);
    }
}
