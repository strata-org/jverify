package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Sibling Java scopes reusing a local's name, where each occupant is first
 * stored inside a loop and read after it, so BOTH declarations hoist to
 * method level: the hoisted declarations must get unique names
 * ({@code t@1}, {@code t@2} -- {@code @} cannot occur in a Java
 * identifier), or Laurel rejects the method with "Duplicate definition".
 */
@JVerifyTest(exitCode = 0, methodsVerified = 3)
class HoistedNameReuse {

    /** {@code int t} in one block, {@code long t} in its sibling. */
    static int reuseAtDifferentTypes(int n) {
        precondition(1 <= n && n <= 10);
        int a = 0;
        {
            int t;
            int i = 0;
            while (true) {
                invariant(0 <= i && i <= n);
                t = i;
                i = i + 1;
                if (i > n) {
                    break;
                }
            }
            if (t >= 0) {
                a = a + 1;
            }
        }
        {
            long t;
            long j = 0;
            while (true) {
                invariant(0 <= j && j <= n);
                t = j;
                j = j + 1;
                if (j > n) {
                    break;
                }
            }
            if (t >= 0) {
                a = a + 1;
            }
        }
        check(0 <= a && a <= 2);
        return a;
    }

    /** The same shape at one type (different slots still collide by name). */
    static int reuseAtTheSameType(int n) {
        precondition(1 <= n && n <= 10);
        int a = 0;
        {
            int t;
            int i = 0;
            while (true) {
                invariant(0 <= i && i <= n);
                t = i;
                i = i + 1;
                if (i > n) {
                    break;
                }
            }
            if (t >= 0) {
                a = a + 1;
            }
        }
        {
            int t;
            int j = 0;
            while (true) {
                invariant(0 <= j && j <= n);
                t = j;
                j = j + 1;
                if (j > n) {
                    break;
                }
            }
            if (t >= 0) {
                a = a + 1;
            }
        }
        check(0 <= a && a <= 2);
        return a;
    }
}
