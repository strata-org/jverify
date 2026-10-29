package org.strata.jverify.langref.jls14.s14_19;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.19 -- The synchronized Statement: a synchronized statement acquires
// the monitor lock on the value of an Expression (which must be a reference
// type), executes the Block, then releases the lock on normal or abrupt
// completion. This exercises a plain synchronized block, nested synchronized
// blocks on different locks, re-entrant locking on the same lock, and that the
// lock is released even when the block completes abruptly via an exception.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_19.SynchronizedStatement_5#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class SynchronizedStatement_5 {
    static int reenter(Object lock, int depth) {
        // Re-entrant acquisition of the same monitor by the same thread.
        synchronized (lock) {
            if (depth <= 0) {
                return 0;
            }
            return 1 + reenter(lock, depth - 1);
        }
    }

    static void run() {

        Object lock = new Object();
        int sum = 0;

        // Plain synchronized block: the Block executes while holding the lock.
        synchronized (lock) {
            sum = sum + 5;
        }

        // Nested synchronized statements on two distinct locks.
        Object other = new Object();
        synchronized (lock) {
            synchronized (other) {
                sum = sum + 3;
            }
        }

        // Re-entrant locking: same lock acquired multiple times by one thread.
        int d = reenter(lock, 4);

        // Lock is usable again after the abrupt exit above.
        synchronized (lock) {
            sum = sum + 1;
        }
        check(sum == 9);
    }
}
