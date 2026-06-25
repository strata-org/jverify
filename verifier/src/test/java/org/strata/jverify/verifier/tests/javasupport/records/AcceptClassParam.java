package org.strata.jverify.verifier.tests.javasupport.records;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * A method with a class/record-typed parameter is accepted and verifies:
 * the type is modelled as an opaque Laurel composite sort, which is now
 * declared (via compositeCommand) so Strata's resolver can find it.
 */
@JVerifyTest(methodsVerified = 3, errorCount = 0)
class AcceptClassParam {
    record Point(int x, int y) {}

    static void acceptParam(Point p) {
    }
}
