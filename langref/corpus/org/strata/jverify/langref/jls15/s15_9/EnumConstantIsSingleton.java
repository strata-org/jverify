package org.strata.jverify.langref.jls15.s15_9;

import static org.strata.jverify.JVerify.*;

// JLS 15.9 -- Class Instance Creation: enum constants are singletons; reference
// identity holds for E.A == E.A.
class EnumConstantIsSingleton {
    enum E { A, B }

    static void run() {

        check(E.A == E.A);
    }
}
