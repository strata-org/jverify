// Java's implicit array exceptions become proof obligations: out-of-bounds
// and negative indexes, a negative allocation size, an element value outside
// the element type's range, and an unguarded read through an array field.
// Each method fails exactly once.
package org.strata.jverify.verifier.tests.javasupport.arrays;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.*;
import static org.strata.jverify.JVerify.*;
@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_ARRAYS}, exitCode = 4, methodsInvalid = 7, errorCount = 7, methodsVerified = 1, methodsSkipped = 0)
class ArrayBoundsFailures {
    int[] data;

    static void readOob() {
        int[] a = new int[10];
        int x = a[10];
//      ^^^^^^^^^^^^^^ Error: precondition could not be proved
    }

    static void writeOob() {
        int[] a = new int[10];
        a[10] = 1;
//      ^^^^^^^^^^ Error: precondition could not be proved
    }

    static void readNeg() {
        int[] a = new int[10];
        int x = a[-1];
//      ^^^^^^^^^^^^^^ Error: precondition could not be proved
    }

    static void writeNeg() {
        int[] a = new int[10];
        a[-1] = 1;
//      ^^^^^^^^^^ Error: precondition could not be proved
    }

    static void negativeSize(int n) {
        int[] a = new int[n];
//      ^^^^^^^^^^^^^^^^^^^^^ Error: non-negative array size does not hold
    }

    static void storeOverflow(int x) {
        int[] a = new int[1];
        a[0] = x + 1;
//      ^^^^^^^^^^^^^ Error: int[] element range could not be proved
    }

    void fieldOob() {
        int x = data[0];
//      ^^^^^^^^^^^^^^^^ Error: precondition does not hold
    }
}
