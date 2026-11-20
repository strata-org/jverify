package org.strata.jverify.langref.jls4.s4_12;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.3 -- Kinds of Variables: exercises the eight kinds of variables:
// class variable (static field), instance variable, array component, method
// parameter, constructor parameter, lambda parameter, exception parameter,
// and local variable.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_12.KindsOfVariables_5#run")
class KindsOfVariables_5 {
    static int classVar = 100;          // class variable
    int instanceVar;                    // instance variable

    KindsOfVariables_5(int ctorParam) {   // constructor parameter
        this.instanceVar = ctorParam;
    }

    int methodWithParam(int methodParam) {  // method parameter
        return methodParam + instanceVar;
    }

    static void run() {

        int local = 5;

        KindsOfVariables_5 obj = new KindsOfVariables_5(50);
        int callResult = obj.methodWithParam(local);
        check(callResult == 55);
    }
}

interface Adder_KindsOfVariables_5 {
    int apply(int x);
}
