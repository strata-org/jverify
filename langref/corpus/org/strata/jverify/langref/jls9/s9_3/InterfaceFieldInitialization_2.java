package org.strata.jverify.langref.jls9.s9_3;

import static org.strata.jverify.JVerify.*;

// JLS 9.3.1 -- Initialization of Fields in Interfaces: interface fields are
// implicitly public static final constants that must have an initializer; the
// initializer may reference earlier interface constants.
class InterfaceFieldInitialization_2 {
    static void run() {

        check(Constants_InterfaceFieldInitialization_2.STEP == 5);
    }
}

interface Constants_InterfaceFieldInitialization_2 {
    int BASE = 10;
    int STEP = 5;
    int DERIVED = BASE + STEP;      // initializer references earlier interface constants
    int NAME_LEN = "hello".length(); // constant-evaluable initializer
}
