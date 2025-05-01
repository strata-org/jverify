// TEST: exitCode=2

package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest
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
//                 ^ error: since &= performs mutation, it may only be used where a statement is allowed
        var a2 = p |= q;
//                 ^ error: since |= performs mutation, it may only be used where a statement is allowed
        var a3 = p ^= q;
//                 ^ error: since ^= performs mutation, it may only be used where a statement is allowed
    } 
}
