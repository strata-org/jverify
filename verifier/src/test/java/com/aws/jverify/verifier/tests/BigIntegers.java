// ^ /builtin-contracts.java(117:22-117:49) Related location: this proposition could not be proved
// ^ /builtin-contracts.java(117:53-117:80) Related location: this proposition could not be proved

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
@JVerifyTest(exitCode = 4, dafnyVerified = 1, dafnyErrors = 4)
class BigIntegers {
    static void test1() {
        BigInteger bi = new BigInteger("23");
        check(bi.intValue() == 23);
        BigInteger bbi = new BigInteger("1");
        BigInteger res = bbi.add(bi);
        check(res.intValue() == 24);
        BigInteger bbi2 = BigInteger.valueOf(12345);
        check(bbi2.intValue() == 12345);
        BigInteger biNeg = new BigInteger("-2");
        check(biNeg.intValue() == -2);
        BigInteger biZero = BigInteger.valueOf(0);
        check(bi.signum() == 1);
        check(biNeg.signum() == -1);
        check(biZero.signum() == 0);
    }

    // Needs a bigger fuel on stringToInt function
    static void test2() {
        BigInteger bi = new BigInteger("234");
        check(bi.intValue() == 234);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion might not hold
//            ^^^^^^^^^^^^^ Error: function precondition could not be proved
//            ^^^^^^^^^^^^^ Error: function precondition could not be proved
// Remy: there are two failing preconditions (both on line 122 of builtin-contracts.java, the LHS and the RHS of the &&), that's why this is reported twice. 
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
