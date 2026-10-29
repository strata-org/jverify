package org.strata.jverify.langref.jls8.s8_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.3.1.1 -- static Fields: a static field is shared across all instances of the class.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_3.StaticField#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class StaticField {
    static int counter = 0;

    static void run() {
        StaticField.counter = 5;
        StaticField.counter = StaticField.counter + 3;
        check(StaticField.counter == 8);
    }
}
