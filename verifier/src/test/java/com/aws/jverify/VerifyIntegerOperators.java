package com.aws.jverify;

import static com.aws.jverify.JVerify.check;

/**
 * Integer operators are those that apply to the types:
 * byte, short, int, long, char
 */
@SuppressWarnings("ConstantValue")
class VerifyIntegerOperators {
    public void foo() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion might not hold

        var l = 3L;
        var r = 3L;
        
        var bitwiseAnd = l & r;
        var bitwiseExclusiveOr = l ^ r;
        var bitwiseInclusiveOr = l | r;
        
        var shiftLeft = l << r;
        var shiftRight = l >> r;
        var unsignedShiftRight = l >>> r;

        l &= r;
        l |= r;
        l ^= r;
        l <<= r;
        l >>= r;
        l >>>= r;
    } 
}
