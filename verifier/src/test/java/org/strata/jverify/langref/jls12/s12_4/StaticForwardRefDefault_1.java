package org.strata.jverify.langref.jls12.s12_4;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 12.4.2 / 8.3.3 -- Initializers run in textual order, and forward
// references to later-declared statics through a method call read the default
// value (0 for int). When `a`'s initializer calls `m()`, `b` has not yet been
// initialized to 10, so it holds its default value of 0.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls12.s12_4.StaticForwardRefDefault_1#run")
class StaticForwardRefDefault_1 {
    static void run() {
        check(C_SFRD1.a == 0);
    }
}

class C_SFRD1 {
    static int a = m();
    static int b = 10;

    static int m() {
        return b;
    }
}
