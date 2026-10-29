package org.strata.jverify.langref.jls12.s12_4;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 12.4.1 -- When Initialization Occurs: a class is initialized at most
// once; subsequent accesses do not re-run its static initializer.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls12.s12_4.StaticInitRunsOnce_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class StaticInitRunsOnce_1 {
    static void run() {
        int first = C_SIRO1.count;
        int second = C_SIRO1.count; // does not re-run the static initializer
        check(first == 1 && second == 1);
    }
}

class C_SIRO1 {
    static int count;
    static {
        count++;
    }
}
