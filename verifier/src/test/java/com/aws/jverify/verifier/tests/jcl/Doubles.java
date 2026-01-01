package com.aws.jverify.verifier.tests.jcl;

import com.aws.jverify.testengine.JVerifyTest;
import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 0, javaVerified = 9, javaErrors = 0)  // 7 test methods + class structure + other symbols = 9 verified
class Doubles {

    static void testLiterals() {
        double d1 = 3.14;
        double d2 = -2.5;
        double d3 = 0.0;
        double d4 = 1.0e10;
        check(d1 == 3.14);
        check(d2 == -2.5);
        check(d3 == 0.0);
        check(d4 == 1.0e10);
    }

    static void testSpecialValues() {
        double nan = Double.NaN;
        double posInf = Double.POSITIVE_INFINITY;
        double negInf = Double.NEGATIVE_INFINITY;
        double maxVal = Double.MAX_VALUE;
        double minVal = Double.MIN_VALUE;
        double minNormal = Double.MIN_NORMAL;

        // NaN is not equal to itself in IEEE 754
        check(!(nan == nan));
        check(nan != nan);

        // Infinity checks
        check(posInf > maxVal);  // Infinity > any finite value  
        check(negInf < -maxVal); // -Infinity < any finite value
        check(posInf == posInf);
        check(negInf == negInf);

        // Boundary values
        check(maxVal > 0);
        check(minVal > 0);
        check(minNormal > minVal); // Testing MIN_NORMAL > MIN_VALUE
    }

    static void testArithmetic() {
        double a = 5.0;
        double b = 2.0;

        check(a + b == 7.0);
        check(a - b == 3.0);
        check(a * b == 10.0);
        check(a / b == 2.5);

        // Compound assignments
        double c = 10.0;
        c += 5.0;
        check(c == 15.0);
        c -= 3.0;
        check(c == 12.0);
        c *= 2.0;
        check(c == 24.0);
        c /= 4.0;
        check(c == 6.0);
    }

    static void testComparisons() {
        double x = 3.14;
        double y = 2.71;
        double z = 3.14;

        check(x > y);
        check(y < x);
        check(x >= z);
        check(x <= z);
        check(x == z);
        check(x != y);
    }

    static void testMathMethods() {
        double neg = -5.5;
        double pos = 3.3;

        check(Math.abs(neg) == 5.5);
        check(Math.abs(pos) == 3.3);

        check(Math.min(neg, pos) == neg);
        check(Math.max(neg, pos) == pos);

        check(Math.sqrt(4.0) == 2.0);
        check(Math.sqrt(9.0) == 3.0);
    }

    static void testClassificationMethods() {
        double nan = Double.NaN;
        double posInf = Double.POSITIVE_INFINITY;
        double normal = 1.0;

        check(Double.isNaN(nan));
        check(!Double.isNaN(normal));

        check(Double.isInfinite(posInf));
        check(!Double.isInfinite(normal));

        check(Double.isFinite(normal));
        check(!Double.isFinite(posInf));
        check(!Double.isFinite(nan));
    }

    static void testIncrementDecrement() {
        double d = 5.0;
        d++;
        check(d == 6.0);
        d--;
        check(d == 5.0);
        ++d;
        check(d == 6.0);
        --d;
        check(d == 5.0);
    }
}