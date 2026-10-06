package org.strata.jverify.langref.jls4.s4_12;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.3 -- Kinds of Variables: exercises the eight kinds of variables:
// class variable (static field), instance variable, array component, method
// parameter, constructor parameter, lambda parameter, exception parameter,
// and local variable.
class KindsOfVariables_3 {
    static int classVar = 100;          // class variable
    int instanceVar;                    // instance variable

    KindsOfVariables_3(int ctorParam) {   // constructor parameter
        this.instanceVar = ctorParam;
    }

    int methodWithParam(int methodParam) {  // method parameter
        return methodParam + instanceVar;
    }

    static void run() {
                          // local variable
        int[] arr = new int[3];                 // array components
        arr[0] = 11;
        arr[1] = 22;
        check(arr[0] == 11 && arr[1] == 22 && arr[2] == 0);
    }
}

interface Adder_KindsOfVariables_3 {
    int apply(int x);
}
