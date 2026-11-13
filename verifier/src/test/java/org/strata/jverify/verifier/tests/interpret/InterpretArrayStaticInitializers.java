package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Arrays in static fields and class initialization. An initializer runs once,
 * at the first use of its class, and may read the array of a class it
 * initializes on the way; a field read through a subclass initializes the
 * class that declares it and no other; an interface's array is built at the
 * first read; and an index out of range in an initializer reaches the use as
 * an {@code ExceptionInInitializerError}, after which the class cannot be
 * used.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_ARRAYS},
        exitCode = 0,
        methodsVerified = 13,
        methodsInvalid = 0,
        methodsSkipped = 0,
        errorCount = 0)
class InterpretArrayStaticInitializers {
    static int[] log = new int[4];
    static int logged;

    static void record(int id) {
        log[logged] = id;
        logged++;
    }

    static class Base {
        static final int[] PRIMES = {2, 3, 5};

        static {
            record(1);
        }
    }

    static class Doubled {
        static {
            record(2);
        }

        static final int[] VALUES = new int[Base.PRIMES.length];
        static int total;

        static {
            for (int k = 0; k < VALUES.length; k++) {
                VALUES[k] = 2 * Base.PRIMES[k];
                total += VALUES[k];
            }
        }
    }

    static class Sub extends Base {
        static int[] own = {7};

        static {
            record(3);
        }
    }

    interface Limits {
        int[] BOUNDS = {-1, 1};
    }

    static class Broken {
        static int[] cells = new int[1];

        static {
            cells[1] = 5;
        }
    }

    @InterpretEntry
    static void run() {
        check(logged == 0 && log.length == 4);

        check(Doubled.total == 20);
        check(logged == 2 && log[0] == 2 && log[1] == 1);
        check(Doubled.VALUES.length == 3 && Doubled.VALUES[0] == 4 && Doubled.VALUES[2] == 10);

        Base.PRIMES[0] = 11;
        check(logged == 2 && Sub.PRIMES[0] == 11 && logged == 2);
        check(Sub.own[0] == 7 && logged == 3 && log[2] == 3);
        check(Doubled.VALUES[0] == 4);

        check(Limits.BOUNDS[0] == -1 && Limits.BOUNDS[1] == 1);
        Limits.BOUNDS[0] = -2;
        check(Limits.BOUNDS[0] == -2);

        int caught = 0;
        try {
            int n = Broken.cells.length;
            check(false);
        } catch (ExceptionInInitializerError e) {
            caught++;
        }
        try {
            Broken.cells = new int[2];
            check(false);
        } catch (NoClassDefFoundError e) {
            caught++;
        }
        check(caught == 2);
    }
}
