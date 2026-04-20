package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;
import static org.strata.jverify.JVerify.*;

@JVerifyTest(skip = "Strata: not yet supported", methodsVerified = 8, errorCount = 0)
class Floats {

    static void testLiterals() {
        float f1 = 3.14f;
        float f2 = -2.5f;
        float f3 = 0.0f;
        float f4 = 1.0e10f;
        check(f1 == 3.14f);
        check(f2 == -2.5f);
        check(f3 == 0.0f);
        check(f4 == 1.0e10f);
    }

    static void testSpecialValues() {
        float nan = Float.NaN;
        float posInf = Float.POSITIVE_INFINITY;
        float negInf = Float.NEGATIVE_INFINITY;
        float maxVal = Float.MAX_VALUE;
        float minVal = Float.MIN_VALUE;
        float minNormal = Float.MIN_NORMAL;

        check(!(nan == nan));
        check(nan != nan);

        check(posInf > maxVal);
        check(negInf < -maxVal);
        check(posInf == posInf);
        check(negInf == negInf);

        check(maxVal > 0);
        check(minVal > 0);
        check(minNormal > minVal);
    }

    static void testArithmetic() {
        float a = 5.0f;
        float b = 2.0f;

        check(a + b == 7.0f);
        check(a - b == 3.0f);
        check(a * b == 10.0f);
        check(a / b == 2.5f);

        float c = 10.0f;
        c += 5.0f;
        check(c == 15.0f);
        c -= 3.0f;
        check(c == 12.0f);
        c *= 2.0f;
        check(c == 24.0f);
        c /= 4.0f;
        check(c == 6.0f);
    }

    static void testComparisons() {
        float x = 3.14f;
        float y = 2.71f;
        float z = 3.14f;

        check(x > y);
        check(y < x);
        check(x >= z);
        check(x <= z);
        check(x == z);
        check(x != y);
    }

    static void testMathMethods() {
        float neg = -5.5f;
        float pos = 3.3f;

        check(Math.abs(neg) == 5.5f);
        check(Math.abs(pos) == 3.3f);

        check(Math.min(neg, pos) == neg);
        check(Math.max(neg, pos) == pos);
    }

    static void testClassificationMethods() {
        float nan = Float.NaN;
        float posInf = Float.POSITIVE_INFINITY;
        float normal = 1.0f;

        check(Float.isNaN(nan));
        check(!Float.isNaN(normal));

        check(Float.isInfinite(posInf));
        check(!Float.isInfinite(normal));

        check(Float.isFinite(normal));
        check(!Float.isFinite(posInf));
        check(!Float.isFinite(nan));
    }

    static void testIncrementDecrement() {
        float f = 5.0f;
        f++;
        check(f == 6.0f);
        f--;
        check(f == 5.0f);
        ++f;
        check(f == 6.0f);
        --f;
        check(f == 5.0f);
    }
}
