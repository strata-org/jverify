package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.postcondition;

/**
 * javac binds {@code c.get(b)} to {@code I.get(Box)}, so nothing proves the result 0: {@code C.get(Object)}, whose
 * contract would, is another overload, and {@code D.get}, which the JVM runs, returns 5.
 */
@JVerifyTest(exitCode = 4, methodsVerified = 6)
class InterfaceOnlyOverload {
    static class Box {
    }

    interface I {
        int get(Box b);
    }

    abstract static class C implements I {
        public int get(Object o) {
            postcondition((int r) -> r == 0);
            return 0;
        }
    }

    static class D extends C {
        public int get(Box b) {
            return 5;
        }
    }

    static void use(C c, Box b) {
        check(c.get(b) == 0);
//      ^^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
    }
}
