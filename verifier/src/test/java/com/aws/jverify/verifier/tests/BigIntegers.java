package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

import java.math.BigInteger;

import static com.aws.jverify.JVerify.*;

@SuppressWarnings({
        "ConstantValue",
        "SizeReplaceableByIsEmpty",
        "OnlyOneElementUsed",
        "StringOperationCanBeSimplified"
})
@JVerifyTest(exitCode = 4, dafnyVerified = 10, dafnyErrors = 3)
class BigIntegers {
    static void test1() {
        BigInteger bi = new BigInteger("23");
        check(bi.intValue() == 23);
        BigInteger bbi = new BigInteger("1");
        BigInteger res = bbi.add(bi);
        check(res.intValue() == 24);
    }

    static void test3() {
        BigInteger bi = new BigInteger("-2");
        check(bi.intValue() == -2);
    }

    // Needs a bigger fuel on stringToInt function
    static void test2() {
        BigInteger bi = new BigInteger("234");
        check(bi.intValue() == 234);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion might not hold
//            ^^^^^^^^^^^^^ Error: function precondition could not be proved

    }

    static void testArith() {
        BigInteger bi = new BigInteger("23");
        BigInteger two = new BigInteger("2");
        check(bi.add(two).intValue() == 25);
        check(bi.subtract(two).intValue() == 21);
        check(bi.multiply(two).intValue() == 46);
        check(bi.divide(two).intValue() == 11);
        check(bi.mod(two).intValue() == 1);
        check(bi.pow(2).intValue() == 529);
        check(bi.negate().intValue() == -23);
        check(bi.abs().intValue() == 23);
        check(bi.negate().abs().intValue() == 23);
    }
}
