package org.strata.jverify.langref.jls8.s8_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.3.2 -- Field Initialization: a field declarator may include a variable initializer,
// evaluated when the declaration is executed (instance) or the class is initialized (static);
// fields without an initializer take the default value for their type.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_3.FieldInitialization_3#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class FieldInitialization_3 {
    int initialized = 3 + 4;
    int defaulted;
    static int staticInitialized = 100;

    static void run() {

        check(staticInitialized == 100);
    }
}
