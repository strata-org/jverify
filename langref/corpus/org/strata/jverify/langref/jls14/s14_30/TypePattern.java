package org.strata.jverify.langref.jls14.s14_30;

import static org.strata.jverify.JVerify.*;

// JLS 14.30.1 -- Kinds of Patterns: a type pattern declares a local variable of a single type.
class TypePattern {
    static void run() {
        Object o = Integer.valueOf(7);
        int result = 0;
        // Type pattern `Integer i` binds o to a fresh local variable i of type Integer.
        if (o instanceof Integer i) {
            result = i.intValue();
        }
        check(result == 7);
    }
}
