// The int[] cases of ArraysVerification: allocation, element writes in a loop
// and a copy between arrays verify. intArrayOfSizeN's check(i == n) does not
// follow from the loop's invariants (none bounds i from above), so it fails.
package org.strata.jverify.verifier.tests.javasupport.arrays;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.*;
import static org.strata.jverify.JVerify.*;
@JVerifyTest(exitCode = 4, methodsInvalid = 1, errorCount = 1, methodsVerified = 3, methodsSkipped = 0)
class IntArraysVerification {
    static void intArrayOfSize10() {
        int[] a = new int[10];
        a[0]=0;
        for (int i = 0; i < a.length; i++) {
            invariant(0 <= i);
            modifies(a);
            invariant(a[0]==0);
            a[i] = i;
        }
        check(a[0]==0);
    }

    static void arrayCopy() {
        int[] tab = new int[4];
        int[] tab2 = new int[4];
        tab2[1] = 10;
        tab[1] = tab2[1];
        check(tab[1]==10);
    }

    static void intArrayOfSizeN(int n) {
        precondition(n>0);
        int[] a = new int[n];
        a[0]=0;
        int i = 0;
        for (i = 0; i < a.length; i++) {
            invariant(0 <= i);
            invariant(a[0]==0);
            modifies(a);
            a[i] = i;
        }
        check(a[0]==0);
        check(i==n);
//      ^^^^^^^^^^^^ Error: assertion could not be proved
    }
}
