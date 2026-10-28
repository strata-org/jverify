package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * Calls to non-transparent methods and local mutation inside a transparent body:
 * both are Laurel's to judge, so the front end passes them through.
 */
@JVerifyTest(errorCount = 0)
public class ImpureExpressionsResolution {
    @Transparent
    int nestedImpureExpressionInPureContext() {
        if (impureM(1) == 1) {
            var x = impureM(impureM(2));
        }
        return impureM(3);
    }
    
    @Transparent
    @SuppressWarnings({"ConstantValue", "StatementWithEmptyBody"})
    int nestedAssignment() {
        var x = 3;
        if ((x += 1) == 4) {
        }
        if (x++ == 4) {
        }
        return 3;
    }
    
    // impure because it is not marked as @Transparent
    int impureM(int input) {
        return input;
    }
}
