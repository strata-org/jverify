package com.aws.jverify;

import static com.aws.jverify.JVerify.check;

/**
 * Integer only operators are those that only apply to the types:
 * byte, short, int, long, char
 */
@SuppressWarnings("ConstantValue")
class ResolutionErrorsIntegerOnlyOperators {
    public void foo() {
        var l = 3f;
        var r = 3f;
    } 
}
