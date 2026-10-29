package org.strata.jverify.langref.jls9.s9_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 9.3.1 -- Initialization of Fields in Interfaces: interface fields are
// implicitly public static final constants that must have an initializer; the
// initializer may reference earlier interface constants.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls9.s9_3.InterfaceFieldInitialization_5#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class InterfaceFieldInitialization_5 {
    static void run() {

        check(Constants_InterfaceFieldInitialization_5.NAME_LEN == 5);
    }
}

interface Constants_InterfaceFieldInitialization_5 {
    int BASE = 10;
    int STEP = 5;
    int DERIVED = BASE + STEP;      // initializer references earlier interface constants
    int NAME_LEN = "hello".length(); // constant-evaluable initializer
}
