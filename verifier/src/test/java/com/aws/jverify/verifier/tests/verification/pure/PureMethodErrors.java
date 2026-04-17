package com.aws.jverify.verifier.tests.verification.pure;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 2)
public class PureMethodErrors {

    @Pure
    int pureWithMultipleStatements() {
        foo();
//      ^ error: except for the last statement, a pure block may only contain variable declarations
        return 3;
    }

    @Pure
    int pureWithInvalidVariableDeclaration() {
        int x;
//          ^ error: variable declaration of 'x' must have an initializer because it is in a pure context
        return 3;
    }
    
    @Pure
    void pureWithoutReturn() {
//       ^ error: pure method should have a return type
        var x = 3;
//          ^ error: a pure block must end in a return or if-else statement
    }

    @Pure
    int pureEndingWithIfNotElse(int x) {
        if (x > 2) {
            if (x > 3) {
//          ^ error: a pure block may not end in an if statement without an else block    
                return 3;
            }
        }
        return 2;
    }
    
    void foo() {}
}
