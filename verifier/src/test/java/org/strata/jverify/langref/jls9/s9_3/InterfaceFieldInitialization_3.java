package org.strata.jverify.langref.jls9.s9_3;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 9.3.1 -- Initialization of Fields in Interfaces: interface fields are
// implicitly public static final constants that must have an initializer; the
// initializer may reference earlier interface constants.
@JVerifyTest(interpret = true, interpreter = "laurel", continueOnErrors = true, entry = "org.strata.jverify.langref.jls9.s9_3.InterfaceFieldInitialization_3#run")
class InterfaceFieldInitialization_3 {
    static void run() {

        check(Constants_InterfaceFieldInitialization_3.DERIVED == 15);
    }
}

interface Constants_InterfaceFieldInitialization_3 {
    int BASE = 10;
    int STEP = 5;
    int DERIVED = BASE + STEP;      // initializer references earlier interface constants
    int NAME_LEN = "hello".length(); // constant-evaluable initializer
//               ^ error: Unsupported call: length
}
