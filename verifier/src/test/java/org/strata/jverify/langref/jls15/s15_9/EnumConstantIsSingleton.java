package org.strata.jverify.langref.jls15.s15_9;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.9 -- Class Instance Creation: enum constants are singletons; reference
// identity holds for E.A == E.A.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_9.EnumConstantIsSingleton#run")
class EnumConstantIsSingleton {
    enum E { A, B }

    static void run() {

        check(E.A == E.A);
    }
}
