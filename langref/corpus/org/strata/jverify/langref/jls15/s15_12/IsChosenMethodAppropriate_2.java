package org.strata.jverify.langref.jls15.s15_12;

import static org.strata.jverify.JVerify.*;

// JLS 15.12.3 -- Is the Chosen Method Appropriate: a compile-time-chosen method must
// match the invocation mode (a static method may be invoked without an instance; an
// instance method requires a target reference). This exercises both a static method
// invoked via the class and an instance method invoked via an object, where each
// chosen method is appropriate for its invocation mode.
class IsChosenMethodAppropriate_2 {
    static void run() {

        // Instance method: appropriate to invoke on an instance reference.
        ChosenHolder_IsChosenMethodAppropriate_2 h = new ChosenHolder_IsChosenMethodAppropriate_2(7);
        int i = h.instanceValue();
        check(i == 7);
    }
}

class ChosenHolder_IsChosenMethodAppropriate_2 {
    int field;

    ChosenHolder_IsChosenMethodAppropriate_2(int v) { this.field = v; }

    static int staticValue() { return 42; }

    int instanceValue() { return field; }
}
