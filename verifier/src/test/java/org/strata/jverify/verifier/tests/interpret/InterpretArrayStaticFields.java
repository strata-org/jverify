package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A static field holds an array as it holds any other reference: the
 * initializer of another class builds its array before the first read, a
 * method reads the array another method assigned, the field and a local name
 * the same array, and assigning the field leaves the array it held as it was.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_ARRAYS},
        exitCode = 0,
        methodsVerified = 9,
        methodsInvalid = 0,
        methodsSkipped = 0,
        errorCount = 0)
class InterpretArrayStaticFields {
    static class Table {
        static final int[] SQUARES = {0, 1, 4, 9};
    }

    static int[] buf;

    static void allocate(int n) {
        buf = new int[n];
    }

    static int last() {
        return buf[buf.length - 1];
    }

    static int[] current() {
        return buf;
    }

    static int sum(int[] a) {
        int total = 0;
        for (int k = 0; k < a.length; k++) {
            total += a[k];
        }
        return total;
    }

    int first() {
        return buf[0];
    }

    @InterpretEntry
    static void run() {
        buf = new int[2];
        buf[1] = Table.SQUARES[3];
        check(buf[1] == 9 && Table.SQUARES.length == 4);
        check(sum(Table.SQUARES) == 14);

        allocate(3);
        check(buf.length == 3 && buf[0] == 0 && buf[1] == 0 && buf[2] == 0);
        buf[2] = 5;
        check(last() == 5);
        buf[0]++;
        buf[0] += 6;
        check(new InterpretArrayStaticFields().first() == 7);

        int[] local = buf;
        local[1] = 8;
        check(local == buf && buf[1] == 8);
        buf[1] = 2;
        check(local[1] == 2);
        check(current() == local && sum(current()) == 14);

        allocate(1);
        check(local != buf && local.length == 3 && local[2] == 5 && buf.length == 1 && buf[0] == 0);
        buf = local;
        check(last() == 5);

        int[] squares = Table.SQUARES;
        squares[0] = 3;
        check(Table.SQUARES[0] == 3 && sum(Table.SQUARES) == 17);

        int caught = 0;
        try {
            buf[3] = 1;
            check(false);
        } catch (ArrayIndexOutOfBoundsException e) {
            caught++;
        }
        try {
            int x = Table.SQUARES[-1];
            check(false);
        } catch (ArrayIndexOutOfBoundsException e) {
            caught++;
        }
        check(caught == 2 && buf.length == 3 && last() == 5);
    }
}
