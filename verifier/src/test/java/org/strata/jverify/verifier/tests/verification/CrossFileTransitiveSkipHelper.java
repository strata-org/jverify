package org.strata.jverify.verifier.tests.verification;

class CrossFileTransitiveSkipHelper {
    static void caller(Object o) {
        boolean b = CrossFileTransitiveSkip.helper(o);
    }
}
