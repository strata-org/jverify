package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.postcondition;

/**
 * javac binds both calls to {@code J.get(Box)}, which the JVM runs: it resolves neither {@code A}'s private
 * {@code get(Box)}, which returns nothing, nor {@code I}'s, an interface's. The {@code get(int)} overloads give the
 * private methods mangled names, so Strata finds only {@code J}'s {@code get}.
 */
@JVerifyTest(methodsVerified = 12)
class PrivateNamesake {
    static class Box {
    }

    static class A {
        private void get(Box b) {
        }

        int get(int k) {
            return k;
        }
    }

    interface I {
        private int get(Box b) {
            postcondition((int r) -> r == 1);
            return 1;
        }

        default int get(int k) {
            return k;
        }
    }

    interface J {
        default int get(Box b) {
            postcondition((int r) -> r == 5);
            return 5;
        }
    }

    static class C extends A implements J {
    }

    static class E implements I, J {
    }

    static void viaClass(C c, Box b) {
        check(c.get(b) == 5);
    }

    static void viaInterface(E e, Box b) {
        check(e.get(b) == 5);
    }
}
