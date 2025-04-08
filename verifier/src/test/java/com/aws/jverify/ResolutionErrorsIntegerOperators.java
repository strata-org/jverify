package com.aws.jverify;

/**
 * Integer operators are those that apply to the types:
 * byte, short, int, long, char
 */
@SuppressWarnings("ConstantValue")
class ResolutionErrorsIntegerOperators {
    public void foo() {
        var l = 3f;
        var r = 3f;
    } 
}
