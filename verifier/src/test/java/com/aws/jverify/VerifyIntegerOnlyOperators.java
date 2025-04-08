package com.aws.jverify;

import static com.aws.jverify.JVerify.check;

/**
 * Integer only operators are those that only apply to the types:
 * byte, short, int, long, char
 */
@SuppressWarnings("ConstantValue")
class VerifyIntegerOnlyOperators {
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
