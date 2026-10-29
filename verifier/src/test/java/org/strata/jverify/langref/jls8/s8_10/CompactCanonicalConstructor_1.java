package org.strata.jverify.langref.jls8.s8_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.10.4.2 -- Compact Canonical Constructors: a compact canonical constructor has no
// formal parameter list; the component parameters are implicit, and the component fields
// are implicitly assigned from those parameters at the end of the constructor body.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_10.CompactCanonicalConstructor_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_RESOLUTION_ERROR})
class CompactCanonicalConstructor_1 {
    record Range(int lo, int hi) {
        // compact canonical constructor: no parameter list; may reassign the implicit
        // parameters before the implicit field assignments occur at the end.
        Range {
            if (lo > hi) {
                int tmp = lo;
                lo = hi;
                hi = tmp;
            }
        }
    }

    static void run() {

        Range r = new Range(5, 2);
        check(r.lo() == 2);
    }
}
