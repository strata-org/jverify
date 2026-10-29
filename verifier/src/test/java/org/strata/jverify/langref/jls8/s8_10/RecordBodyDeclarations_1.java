package org.strata.jverify.langref.jls8.s8_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.10.2 -- Record Body Declarations: the body of a record declaration may contain
// static fields, static methods, instance methods, and other members in addition to
// the implicitly declared members. (Instance fields cannot be declared explicitly.)
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_10.RecordBodyDeclarations_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_RESOLUTION_ERROR})
class RecordBodyDeclarations_1 {
    record Counter(int value) {
        static int STEP = 1;

        static Counter origin() {
            return new Counter(0);
        }

        Counter incremented() {
            return new Counter(value + STEP);
        }
    }

    static void run() {

        Counter c = Counter.origin();
        check(c.value() == 0);
    }
}
