// TEST: exitCode=2

package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;

/**
 * Numeric operators are those that apply to the types:
 * byte, short, int, long, float, double, char
 */
@SuppressWarnings("ConstantValue")
@JVerifyTest
class ResolutionErrorsNumericOperators {
    public void foo() {
        var l = 3;
        var r = 3;
        var incrementPostfix = l++;
//                              ^ error: since ++ performs mutation, it may only be used where a statement is allowed
        check(incrementPostfix == 3);
        check(l == 4L);
        var decrementPostfix = l--;
//                              ^ error: since -- performs mutation, it may only be used where a statement is allowed
        check(decrementPostfix == 4);
        check(l == 3L);
        var incrementPrefix = ++l;
//                            ^ error: since ++ performs mutation, it may only be used where a statement is allowed
        check(incrementPrefix == 4);
        check(l == 4f);
        var decrementPrefix = --l;
//                            ^ error: since -- performs mutation, it may only be used where a statement is allowed
        check(decrementPrefix == 3);
        check(l == 3);
        
        var plusEquals = l += r;
//                         ^ error: since += performs mutation, it may only be used where a statement is allowed
        var minEquals = l -= r;
//                        ^ error: since -= performs mutation, it may only be used where a statement is allowed
        var mulEquals = l *= r;
//                        ^ error: since *= performs mutation, it may only be used where a statement is allowed
        var divEquals = l /= r;
//                        ^ error: since /= performs mutation, it may only be used where a statement is allowed
        var modEquals = l %= r;
//                        ^ error: since %= performs mutation, it may only be used where a statement is allowed
    } 
}
