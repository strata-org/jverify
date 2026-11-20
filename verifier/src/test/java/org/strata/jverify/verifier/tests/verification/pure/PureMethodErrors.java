package org.strata.jverify.verifier.tests.verification.pure;

import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * Body shapes the deleted Dafny-era shape rules used to reject. Laurel accepts them
 * in a transparent body, so the front end passes them through.
 */
@JVerifyTest(errorCount = 0)
public class PureMethodErrors {

    @Transparent
    int pureWithMultipleStatements() {
        foo();
        return 3;
    }

    @Transparent
    void pureWithoutReturn() {
        var x = 3;
    }

    @Transparent
    int pureEndingWithIfNotElse(int x) {
        if (x > 2) {
            if (x > 3) {
                return 3;
            }
        }
        return 2;
    }
    
    void foo() {}
}
