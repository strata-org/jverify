package org.strata.jverify.langref.jls6.s6_6;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 6.6.1 -- Determining Accessibility: a private method is accessible from
// anywhere in the body of the enclosing top-level class.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls6.s6_6.AccessPrivate_2#run")
class AccessPrivate_2 {
    private static int hidden() {
        return 7;
    }

    static void run() {
        check(hidden() == 7);
    }
}
