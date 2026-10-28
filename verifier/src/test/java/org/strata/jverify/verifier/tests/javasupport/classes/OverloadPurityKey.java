package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * Purity is a property of one OVERLOAD, not of a name.
 *
 * <p>{@code Box.set(int)} writes the heap; {@code Box.set(boolean)} is {@code @Transparent}.
 * A frontend that records purity per name keeps whichever declaration it saw last --
 * here the pure one, which is declared second on purpose -- and then treats the
 * impure call in {@code caller} as pure. {@code caller} is then given no heap frame
 * while calling a method that writes the heap.
 *
 * <p>This was unreachable while overloads were refused outright; it became reachable
 * once overloaded names were mangled instead. Verified 5 = both {@code set}
 * overloads + {@code caller} + the implicit constructors of the fixture and Box.
 */
@JVerifyTest(methodsVerified = 5, errorCount = 0)
class OverloadPurityKey {
    static class Box {
        int v;

        void set(int x) {
            this.v = x;
        }

        @Transparent
        int set(boolean b) {
            return b ? 1 : 0;
        }
    }

    static void caller(Box b) {
        b.set(5);
    }
}
