package org.strata.jverify.langref.jls8.s8_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.10.4.1 -- Normal Canonical Constructors: a canonical constructor may be declared
// explicitly with a formal parameter list that matches the record's components; it must
// assign each component field, typically ending with this.x = x for every component.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_10.NormalCanonicalConstructor_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_RESOLUTION_ERROR})
class NormalCanonicalConstructor_2 {
    record Fraction(int num, int den) {
        // explicit normal canonical constructor with validation/normalization
        Fraction(int num, int den) {
            if (den < 0) {
                num = -num;
                den = -den;
            }
            this.num = num;
            this.den = den;
        }
    }

    static void run() {

        Fraction f = new Fraction(1, -2);

        check(f.den() == 2);
    }
}
