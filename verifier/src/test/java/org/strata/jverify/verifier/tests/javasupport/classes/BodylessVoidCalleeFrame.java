package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

/**
 * A void interface or abstract method without a contract may write any heap
 * location, so a call to one must not let the caller carry a field fact across
 * it, and an override that writes the heap refines its frame. A non-void one
 * without a contract keeps an empty frame.
 */
@JVerifyTest(exitCode = 4, methodsVerified = 10, errorCount = 3)
class BodylessVoidCalleeFrame {
    static class Box {
        int c;
    }

    interface I {
        void bump(Box b);
    }

    static class Impl implements I {
        public void bump(Box b) {
            b.c = 1;
        }
    }

    abstract static class A {
        abstract void bump(Box b);
    }

    static class AImpl extends A {
        void bump(Box b) {
            b.c = 1;
        }
    }

    interface J {
        int get(Box b);
    }

    static class JImpl implements J {
        public int get(Box b) {
            return b.c;
        }
    }

    static void viaInterface(I i, Box b) {
        b.c = 0;
        i.bump(b);
        check(b.c == 0);
//      ^^^^^^^^^^^^^^^^ Error: assertion could not be proved
    }

    static void viaAbstract(A a, Box b) {
        b.c = 0;
        a.bump(b);
        check(b.c == 0);
//      ^^^^^^^^^^^^^^^^ Error: assertion could not be proved
    }

    static void viaConcrete(Impl i, Box b) {
        b.c = 0;
        i.bump(b);
        check(b.c == 0);
//      ^^^^^^^^^^^^^^^^ Error: assertion could not be proved
    }

    static void nonVoidKeepsFrame(J j, Box b) {
        b.c = 0;
        j.get(b);
        check(b.c == 0);
    }
}
