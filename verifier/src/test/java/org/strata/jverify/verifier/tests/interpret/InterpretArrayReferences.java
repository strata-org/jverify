package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * An array is a reference: two variables, a parameter, a result and an instance
 * field can all name the same array, and a write through one is seen through
 * the others. Two arrays with equal elements are different references.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_ARRAYS},
        exitCode = 0,
        methodsVerified = 6,
        methodsInvalid = 0,
        methodsSkipped = 0,
        errorCount = 0)
class InterpretArrayReferences {
    static class Holder {
        int[] items;

        Holder(int[] items) {
            this.items = items;
        }
    }

    static int[] squares(int n) {
        int[] a = new int[n];
        for (int k = 0; k < n; k++) {
            a[k] = k * k;
        }
        return a;
    }

    static void fill(byte[] a, byte value) {
        for (int k = 0; k < a.length; k++) {
            a[k] = value;
        }
    }

    static int sum(int[] a) {
        int total = 0;
        for (int k = 0; k < a.length; k++) {
            total += a[k];
        }
        return total;
    }

    @InterpretEntry
    static void run() {
        int[] a = squares(4);
        check(a.length == 4 && a[0] == 0 && a[1] == 1 && a[2] == 4 && a[3] == 9);
        check(sum(a) == 14);

        int[] alias = a;
        alias[0] = 7;
        check(a == alias && a[0] == 7);

        int[] other = squares(4);
        check(a != other && other[0] == 0);

        byte[] bytes = new byte[3];
        fill(bytes, (byte) -1);
        check(bytes[0] == -1 && bytes[1] == -1 && bytes[2] == -1);

        Holder holder = new Holder(a);
        holder.items[1] = 5;
        check(holder.items == a && a[1] == 5 && holder.items.length == 4);
        check(sum(holder.items) == 25);
        holder.items = other;
        check(holder.items[1] == 1 && a[1] == 5);
    }
}
