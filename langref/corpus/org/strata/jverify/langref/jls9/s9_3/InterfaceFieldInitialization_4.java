package org.strata.jverify.langref.jls9.s9_3;

import static org.strata.jverify.JVerify.*;

// JLS 9.3.1 -- Initialization of Fields in Interfaces: interface fields are
// implicitly public static final constants that must have an initializer; the
// initializer may reference earlier interface constants.
class InterfaceFieldInitialization_4 {
    static void run() {

        int total = Constants_InterfaceFieldInitialization_4.BASE + Constants_InterfaceFieldInitialization_4.STEP + Constants_InterfaceFieldInitialization_4.DERIVED;
        check(total == 30);
    }
}

interface Constants_InterfaceFieldInitialization_4 {
    int BASE = 10;
    int STEP = 5;
    int DERIVED = BASE + STEP;      // initializer references earlier interface constants
    int NAME_LEN = "hello".length(); // constant-evaluable initializer
}
