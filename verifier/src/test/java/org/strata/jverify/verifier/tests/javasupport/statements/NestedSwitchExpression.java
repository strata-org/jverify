package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 0)
class NestedSwitchExpression {

    static int g(int a, int b) {
        int r = 0;
        switch (a) {
            case 0:
                r = switch (b) {
                    case 0 -> 1;
                    default -> 2;
                };
                break;
            default:
                r = 3;
        }
        check(r >= 1);
        return r;
    }
}
