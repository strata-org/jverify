// A program class overriding a contracted library member refuses the callers
// of that member: override refinement against library contracts is not
// supported.
package org.strata.jverify.verifier.tests.jcl;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER}, continueOnErrors = true, methodsSkipped = 1)
class LibraryMemberOverride {
    static boolean same(Object o, Object p) {
//                                          ^ error: call to 'equals', which could not be translated: overridden by 'org.strata.jverify.verifier.tests.jcl.LibraryMemberOverride.Overrider'; override refinement against library contracts is not yet supported
        return o.equals(p);
    }

    static int ok(int x) {
        return x;
    }

    static class Overrider {
        @Override
        public boolean equals(Object o) {
            return true;
        }
    }
}
