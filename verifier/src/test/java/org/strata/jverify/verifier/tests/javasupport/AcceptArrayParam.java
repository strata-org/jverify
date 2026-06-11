package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * A method with an array-typed parameter is accepted and verifies: the array
 * is modelled as a Laurel Map<int, int>. (Element-level get/set translate and
 * type-check but do not fully verify yet — the element type is erased to an
 * unbounded int rather than int32; full element-level verification is deferred
 * to typed-array support, Strata#1073.)
 */
@JVerifyTest(methodsVerified = 2, errorCount = 0)
class AcceptArrayParam {
    static void acceptArrayParam(int[] a) {
    }
}
