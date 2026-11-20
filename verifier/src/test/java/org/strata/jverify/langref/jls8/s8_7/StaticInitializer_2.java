package org.strata.jverify.langref.jls8.s8_7;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.7 -- Static Initializers: a static initializer block declared in a class is executed
// when the class is initialized, in textual order with any static field initializers. Here a
// static field is set inside a static initializer block and observed from run().
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_7.StaticInitializer_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class StaticInitializer_2 {
    static int computed;
    static int doubled;

    static {
        computed = 6 * 7;
    }

    static {
        doubled = computed * 2;
    }

    static void run() {

        check(doubled == 84);
    }
}
