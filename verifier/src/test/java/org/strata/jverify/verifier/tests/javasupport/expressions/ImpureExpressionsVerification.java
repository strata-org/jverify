package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 22)
public class ImpureExpressionsVerification {
    @Pure
    int nestedImpureExpressionInPureContext() {
        if (impureM(1) == 1) {
//          ^^^^^^^ Error: expression is not allowed to invoke a method (impureM)
            return impureM(impureM(2));
//                 ^^^^^^^ Error: expression is not allowed to invoke a method (impureM)
//                         ^^^^^^^ Error: expression is not allowed to invoke a method (impureM)
        }
        return impureM(3);
//             ^^^^^^^ Error: expression is not allowed to invoke a method (impureM)
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
    
    // impure because it is not marked as Pure
    int impureM(int input) {
        return input;
    }
}
