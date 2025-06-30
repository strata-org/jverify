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
        BigInteger twentyFive = bi.add(two);
        check(twentyFive.intValue() == 25);
        BigInteger twentyOne = bi.subtract(two);
        check(twentyOne.intValue() == 21);
        BigInteger fortySix = bi.multiply(two);
        check(fortySix.intValue() == 46);
        BigInteger eleven = bi.divide(two);
        check(eleven.intValue() == 11);
        BigInteger one = bi.mod(two);
        check(one.intValue() == 1);
        BigInteger large = bi.pow(2);
        check(large.intValue() == 529);
        BigInteger minusTwentyThree = bi.negate();
        check(minusTwentyThree.intValue() == -23);
        BigInteger twentyThree = bi.abs();
        check(twentyThree.intValue() == 23);
        BigInteger twentyThreeAgain = minusTwentyThree.abs();
        check(twentyThreeAgain.intValue() == 23);
        BigInteger minusTwo = new BigInteger("-2");
        BigInteger fail = bi.add(minusTwo);
        check(fail.intValue() == 25);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion might not hold
    }

}
