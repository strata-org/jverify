package org.strata.jverify.langref.jls12.s12_5;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 12.5 / 8.8.7.1 -- Explicit Constructor Invocations: `this(...)` chains
// to another constructor of the same class before running the current
// constructor's remaining body.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls12.s12_5.ThisCtorChain_1#run")
class ThisCtorChain_1 {
    static void run() {
        check(new C_TCC1().v == 5);
    }
}

class C_TCC1 {
    int v;

    C_TCC1() {
        this(5);
    }

    C_TCC1(int x) {
        v = x;
    }
}
