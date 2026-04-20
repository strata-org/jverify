package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * Integer operators are those that apply to the types:
 * byte, short, int, long, char
 * <p>
 * Since none of integer specific operators are currently supported,
 * there currently is no corresponding 'verify integer operators' file
 */
@SuppressWarnings("ConstantValue")
@JVerifyTest(skip = "Strata: not yet supported", exitCode = 2)
class ResolutionErrorsIntegerOperators {
    public void foo() {
        var l = 3;
        var r = 3;

        var bitwiseAnd = l & r;
//                         ^ error: operator &(int,int) is not supported
        var bitwiseInclusiveOr = l | r;
//                                 ^ error: operator |(int,int) is not supported
        var bitwiseExclusiveOr = l ^ r;
//                                 ^ error: operator ^(int,int) is not supported
        var bitWiseComplement = ~l;
//                              ^ error: operator ~(int) is not supported

        var shiftLeft = l << r;
//                        ^ error: operator <<(int,int) is not supported
        var shiftRight = l >> r;
//                         ^ error: operator >>(int,int) is not supported
        var unsignedShiftRight = l >>> r;
//                                 ^ error: operator >>>(int,int) is not supported

        l &= r;
//        ^ error: operator &(int,int) is not supported
        l |= r;
//        ^ error: operator |(int,int) is not supported
        l ^= r;
//        ^ error: operator ^(int,int) is not supported
        l <<= r;
//        ^ error: operator <<(int,int) is not supported
        l >>= r;
//        ^ error: operator >>(int,int) is not supported
        l >>>= r;
//        ^ error: operator >>>(int,int) is not supported

        var a1 = l &= r;
//                 ^ error: operator &(int,int) is not supported
        var a2 = l |= r;
//                 ^ error: operator |(int,int) is not supported
        var a3 = l ^= r;
//                 ^ error: operator ^(int,int) is not supported
        var a4 = l <<= r;
//                 ^  error: operator <<(int,int) is not supported
        var a5 = l >>= r;
//                 ^  error: operator >>(int,int) is not supported
        var a6 = l >>>= r;
//                 ^ error: operator >>>(int,int) is not supported
    } 
}
