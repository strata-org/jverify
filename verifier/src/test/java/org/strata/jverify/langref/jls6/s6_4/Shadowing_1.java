package org.strata.jverify.langref.jls6.s6_4;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 6.4.1 -- Shadowing: a local variable declaration shadows a field
// declaration of the same name for the remainder of its enclosing scope.
// Here, method-local `x` shadows the class field `x`.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls6.s6_4.Shadowing_1#run")
class Shadowing_1 {
    static int x = 1;

    static void run() {
        int x = 2;
        check(x == 2);
    }
}
