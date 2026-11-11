package org.strata.jverify.verifier.tests.javasupport.statics;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * JVMS 5.5, step 7: before a class is initialized, its superinterfaces that
 * declare a default method are, each after its own superinterfaces, so
 * {@code J} runs before {@code I extends J}. An interface whose only method
 * is private declares no default method and is not initialized.
 */
@JVerifyTest(interpret = true, entry = "org.strata.jverify.verifier.tests.javasupport.statics.InterfaceInitializationOrder#run")
class InterfaceInitializationOrder {

    // boolean statics only: the Laurel pipeline lets no entry procedure reach
    // a global of a constrained type such as int, not even through a call.
    static boolean jRan;
    static boolean iAfterJ;
    static boolean kRan;

    static boolean markJ() {
        jRan = true;
        return true;
    }

    static boolean markI() {
        iAfterJ = jRan;
        return true;
    }

    static boolean markK() {
        kRan = true;
        return true;
    }

    interface J {
        boolean A = markJ();

        default void j() {
        }
    }

    interface I extends J {
        boolean B = markI();

        default void i() {
        }
    }

    interface K {
        boolean D = markK();

        private void helper() {
        }
    }

    static class C implements I, K {
        static boolean c;
    }

    static void run() {
        boolean x = C.c;
        check(jRan);
        check(iAfterJ);
        check(!kRan);
    }
}
