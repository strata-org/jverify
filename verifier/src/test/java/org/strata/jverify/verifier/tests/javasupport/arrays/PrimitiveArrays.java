// One-dimensional primitive arrays: zero initialization of every element
// kind, lengths, initializers, stores and reads per kind, compound
// assignment, loops with invariants over elements, array parameters, results
// and fields. Every method verifies.
package org.strata.jverify.verifier.tests.javasupport.arrays;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.*;
import static org.strata.jverify.JVerify.*;
@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_ARRAYS}, exitCode = 0, methodsInvalid = 0, errorCount = 0, methodsVerified = 17, methodsSkipped = 0)
class PrimitiveArrays {
    int[] data;
    int count;

    static void zeroInit() {
        int[] i = new int[2];
        long[] l = new long[2];
        short[] s = new short[2];
        byte[] b = new byte[2];
        char[] c = new char[2];
        boolean[] z = new boolean[2];
        check(i[1] == 0 && l[1] == 0 && s[1] == 0 && b[1] == 0 && c[1] == 0 && !z[1]);
    }

    static void lengthOfNew(int n) {
        precondition(n >= 0);
        int[] a = new int[n];
        check(a.length == n);
    }

    static void initializer() {
        int[] a = {1, 2, 3};
        check(a.length == 3 && a[0] == 1 && a[2] == 3);
    }

    static void byteAndBoolean() {
        byte[] b = new byte[1];
        b[0] = 5;
        boolean[] z = new boolean[1];
        z[0] = true;
        check(b[0] == 5 && z[0]);
    }

    static void charAndLong() {
        char[] c = {'a', 'b'};
        long[] l = {1L, 2L};
        l[1] = 7L;
        check(c[1] == 'b' && l[1] == 7L);
    }

    static void compound() {
        int[] a = new int[2];
        a[0] += 2;
        a[1]++;
        byte[] b = new byte[1];
        b[0]++;
        check(a[0] == 2 && a[1] == 1 && b[0] == 1);
    }

    static int sum(int[] a) {
        precondition(a.length <= 1000);
        precondition(forall((int j) -> implies(0 <= j && j < a.length, 0 <= a[j] && a[j] <= 100)));
        int s = 0;
        for (int i = 0; i < a.length; i++) {
            invariant(0 <= i && i <= a.length);
            invariant(0 <= s && s <= 100 * i);
            s = s + a[i];
        }
        return s;
    }

    // No method-level modifies(a): a loop that writes the heap loses its
    // frame in the backend (followup B5), so only modifies * verifies here.
    static void fillFirst(int[] a) {
        precondition(a.length > 0);
        a[0] = 7;
        for (int i = 1; i < a.length; i++) {
            modifies(a);
            invariant(1 <= i);
            invariant(a[0] == 7);
            a[i] = 1;
        }
        check(a[0] == 7);
    }

    static int first(int[] a) {
        precondition(a.length > 0);
        return a[0];
    }

    static int[] make(int n) {
        precondition(n >= 0);
        postcondition((int[] r) -> r.length == n);
        return new int[n];
    }

    static void useMake() {
        int[] r = make(3);
        r[0] = 4;
        check(r[0] == 4);
    }

    static void doWhileArray(int[] a) {
        precondition(a.length == 3);
        int i = 0;
        do {
            modifies(a);
            invariant(0 <= i && i < 3);
            a[i] = 1;
            i++;
        } while (i < 3);
        check(a[2] == 1);
    }

    static int[] copyOf3(int[] a) {
        precondition(a.length == 3);
        postcondition((int[] r) -> r.length == 3);
        int[] r = new int[3];
        for (int i = 0; i < 3; i++) {
            invariant(0 <= i);
            r[i] = a[i];
        }
        return r;
    }

    void setFirst(int v) {
        modifies(data);
        precondition(data.length > 0);
        data[0] = v;
        check(data[0] == v);
    }

    void setFirstKeepsCount() {
        modifies(data);
        precondition(data.length > 0);
        int c = count;
        data[0] = 1;
        check(count == c);
    }

    void countData() {
        int n = 0;
        for (int i = 0; i < data.length; i++) {
            invariant(0 <= i && i <= data.length);
            invariant(n == i);
            n++;
        }
        check(n == data.length);
    }
}
