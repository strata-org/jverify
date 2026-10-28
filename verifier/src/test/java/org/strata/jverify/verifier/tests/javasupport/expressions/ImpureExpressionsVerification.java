package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(errorCount = 0)
public class ImpureExpressionsVerification {
    @Transparent
    int nestedImpureExpressionInPureContext() {
        if (impureM(1) == 1) {
            return impureM(impureM(2));
        }
        return impureM(3);
    }
    
    int nestedImpureExpression() {
        if (impureM(1) == 1) {
            var x = impureM(impureM(2));
        }
        return impureM(3);
    }
    
    @SuppressWarnings("ConstantValue")
    void nestedAssignment() {
        var x = 3;
        if ((x += 1) == 4) {
            var y = 3;
        }
        if (x++ == 4) {
            var y = 3;
        }
    }
    
    // impure because it is not marked as @Transparent
    int impureM(int input) {
        return input;
    }
}
