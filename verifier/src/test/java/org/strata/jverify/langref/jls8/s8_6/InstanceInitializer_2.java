package org.strata.jverify.langref.jls8.s8_6;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.6 -- Instance Initializers: an instance initializer block runs when an
// instance is created, after super() and before the constructor body completes.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_6.InstanceInitializer_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class InstanceInitializer_2 {
    int a;
    int b;

    // Instance initializer block: executes during construction.
    {
        a = 7;
        b = a + 3;
    }

    static void run() {

        InstanceInitializer_2 obj = new InstanceInitializer_2();
        check(obj.b == 10);
    }
}
