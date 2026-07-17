package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.testengine.Blocker;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_DIAGNOSTIC}, exitCode = 2)
@SuppressWarnings("ConstantValue")
class ResolutionErrorsBooleanOperators {
    public void foo() {
        var p = true;
        var q = false;

        var bitwiseAnd = p & q;
//                         ^ error: operator &(boolean,boolean) is not supported
        var bitwiseOr = p | q;
//                        ^ error: operator |(boolean,boolean) is not supported
        var xor = p ^ q;
//                  ^ error: operator ^(boolean,boolean) is not supported

        p &= q;
//        ^ error: operator &(boolean,boolean) is not supported
        p |= q;
//        ^ error: operator |(boolean,boolean) is not supported
        p ^= q;
//        ^ error: operator ^(boolean,boolean) is not supported
        
        var a1 = p &= q;
//                 ^ error: operator &(boolean,boolean) is not supported
        var a2 = p |= q;
//                 ^ error: operator |(boolean,boolean) is not supported
        var a3 = p ^= q;
//                 ^ error: operator ^(boolean,boolean) is not supported
    } 
}
