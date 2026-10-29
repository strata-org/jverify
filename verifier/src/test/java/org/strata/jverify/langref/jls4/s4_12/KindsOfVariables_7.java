package org.strata.jverify.langref.jls4.s4_12;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.3 -- Kinds of Variables: exercises the eight kinds of variables:
// class variable (static field), instance variable, array component, method
// parameter, constructor parameter, lambda parameter, exception parameter,
// and local variable.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_12.KindsOfVariables_7#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class KindsOfVariables_7 {
    static int classVar = 100;          // class variable
    int instanceVar;                    // instance variable

    KindsOfVariables_7(int ctorParam) {   // constructor parameter
        this.instanceVar = ctorParam;
    }

    int methodWithParam(int methodParam) {  // method parameter
        return methodParam + instanceVar;
    }

    static void run() {

        int caught = 0;
        try {
            throw new RuntimeException("boom");
        } catch (RuntimeException e) {          // exception parameter e
            caught = e.getMessage().length();
        }
        check(caught == 4);
    }
}

interface Adder_KindsOfVariables_7 {
    int apply(int x);
}
