package org.strata.jverify.langref.jls8.s8_9;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.9.3 -- Enum implements Comparable<E>: compareTo orders constants by
// their declaration position (ordinal).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_9.Enum_CompareTo_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Enum_CompareTo_1 {
    enum E {
        A, B, C
    }

    static void run() {
        check(E.A.compareTo(E.C) < 0);
    }
}
