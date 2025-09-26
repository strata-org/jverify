package com.aws.jverify.verifier.tests.javasupport.expressions;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 2)
public class ImpureExpressionsResolution {
    @Pure
    int nestedImpureExpressionInPureContext() {
        if (impureM(1) == 1) {
            var x = impureM(impureM(2));
//              ^ error: a pure block must end in a return or if-else statement
        }
        return impureM(3);
    }
    
    @Pure
    @SuppressWarnings({"ConstantValue", "StatementWithEmptyBody"})
    int nestedAssignment() {
        var x = 3;
        if ((x += 1) == 4) {
//             ^ error: since += performs mutation, it may not be used in a pure context
//                         ^ error: a pure block must end in a return or if-else statement
        }
        if (x++ == 4) {
//           ^ error: since ++ performs mutation, it may not be used in a pure context
//                    ^ error: a pure block must end in a return or if-else statement
        }
        return 3;
    }
    
    // impure because it is not marked as Pure
    int impureM(int input) {
        return input;
    }
}
