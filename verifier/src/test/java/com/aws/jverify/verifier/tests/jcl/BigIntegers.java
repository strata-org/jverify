// ^ /builtin-contracts.java(589:22-589:51) Related location: this proposition could not be proved
// ^ /builtin-contracts.java(589:55-589:84) Related location: this proposition could not be proved
package com.aws.jverify.verifier.tests.jcl;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import java.math.BigInteger;

import static com.aws.jverify.JVerify.*;

@SuppressWarnings({
        "ConstantValue",
        "SizeReplaceableByIsEmpty",
        "OnlyOneElementUsed",
        "StringOperationCanBeSimplified"
})
@JVerifyTest(exitCode = 4, dafnyVerified = 5, dafnyErrors = 4, useBuiltinContracts = true, javaVerified = 4, javaErrors = 2)
class BigIntegers {
    static void testConstructors() {
        BigInteger bi = new BigInteger("3");
        check(bi.intValue() == 3);
        BigInteger bbi = new BigInteger("1");
        check(bbi.intValue() == 1);
        BigInteger biNeg = new BigInteger("-2");
        check(biNeg.intValue() == -2);
        BigInteger bbi2 = BigInteger.valueOf(12345);
        check(bbi2.intValue() == 12345);
    }

    static void testComparisons() {
        BigInteger bi = new BigInteger("3");
        BigInteger biNeg = new BigInteger("-2");
        BigInteger biZero = BigInteger.valueOf(0);
        check(bi.signum() == 1);
        check(biNeg.signum() == -1);
        check(biZero.signum() == 0);
        int cmp = bi.compareTo(biNeg);
        check(cmp > 0);
        BigInteger biAgain = new BigInteger("3");
        cmp = bi.compareTo(biAgain);
        check(cmp == 0);
        cmp = biNeg.compareTo(biZero);
        check(cmp < 0);
    }

    // Needs a bigger fuel on stringToInt function
    static void testConstructorNegative() {
        BigInteger bi = new BigInteger("23456");
        check(bi.intValue() == 23456);
//            ^^^^^^^^^^^^^ Error: function precondition could not be proved
//            ^^^^^^^^^^^^^ Error: function precondition could not be proved        
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
    }

    // Test all arithmetic operations on BigIntegers
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
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
    }
    
    @Pure
    BigInteger createInPure() {
        return new BigInteger("2");
    }

}
