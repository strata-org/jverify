package com.aws.jverify;

import static com.aws.jverify.JVerify.check;

/**
 * Numeric operators are those that apply to the types:
 * byte, short, int, long, float, double, char
 */
@SuppressWarnings("ConstantValue")
class ResolutionErrorsNumericOperators {
    public void foo() {
        var l = 3f;
        var r = 3f;
        var incrementPostfix = l++;
//                              ^ error: unary operator ++(float) is not supported
        check(incrementPostfix == 3f);
        check(l == 4L);
        var decrementPostfix = l--;
//                              ^ error: unary operator --(float) is not supported
        check(decrementPostfix == 4f);
        check(l == 3L);
        var incrementPrefix = ++l;
//                            ^ error: unary operator ++(float) is not supported
        check(incrementPrefix == 4f);
        check(l == 4f);
        var decrementPrefix = --l;
//                            ^ error: unary operator --(float) is not supported
        check(decrementPrefix == 3f);
        check(l == 3f);
        
        var negation = -l;
//                     ^ error: unary operator -(float) is not supported
        check(negation == -3L);
        
        var plusEquals = l += r;
//                         ^ error: since '+=' performs mutation, it may only be used where a statement is allowed
        var minEquals = l -= r;
//                        ^ error: since '-=' performs mutation, it may only be used where a statement is allowed
        var mulEquals = l *= r;
//                        ^ error: since '*=' performs mutation, it may only be used where a statement is allowed
        var divEquals = l /= r;
//                        ^ error: since '/=' performs mutation, it may only be used where a statement is allowed
        var modEquals = l %= r;
//                        ^ error: since '%=' performs mutation, it may only be used where a statement is allowed
    } 
}
