package org.strata.jverify.langref.jls15.s15_12;

import static org.strata.jverify.JVerify.*;

// JLS 15.12.3 -- Is the Chosen Method Appropriate: a compile-time-chosen method must
// match the invocation mode (a static method may be invoked without an instance; an
// instance method requires a target reference). This exercises both a static method
// invoked via the class and an instance method invoked via an object, where each
// chosen method is appropriate for its invocation mode.
class IsChosenMethodAppropriate_1 {
    static void run() {

        // Static method: appropriate to invoke without an instance.
        int s = ChosenHolder_IsChosenMethodAppropriate_1.staticValue();
        check(s == 42);
    }
}

class ChosenHolder_IsChosenMethodAppropriate_1 {
    int field;

    ChosenHolder_IsChosenMethodAppropriate_1(int v) { this.field = v; }

    static int staticValue() { return 42; }

    int instanceValue() { return field; }
}
