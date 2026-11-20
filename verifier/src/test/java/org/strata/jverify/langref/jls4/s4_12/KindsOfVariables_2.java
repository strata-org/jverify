package org.strata.jverify.langref.jls4.s4_12;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.3 -- Kinds of Variables: exercises the eight kinds of variables:
// class variable (static field), instance variable, array component, method
// parameter, constructor parameter, lambda parameter, exception parameter,
// and local variable.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_12.KindsOfVariables_2#run")
class KindsOfVariables_2 {
    static int classVar = 100;          // class variable
    int instanceVar;                    // instance variable

    KindsOfVariables_2(int ctorParam) {   // constructor parameter
        this.instanceVar = ctorParam;
    }

    int methodWithParam(int methodParam) {  // method parameter
        return methodParam + instanceVar;
    }

    static void run() {

        int local = 5;

        KindsOfVariables_2 obj = new KindsOfVariables_2(50);
        int callResult = obj.methodWithParam(local);
        check(obj.instanceVar == 50);
    }
}

interface Adder_KindsOfVariables_2 {
    int apply(int x);
}
