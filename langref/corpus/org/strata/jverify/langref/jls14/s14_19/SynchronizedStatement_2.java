package org.strata.jverify.langref.jls14.s14_19;

import static org.strata.jverify.JVerify.*;

// JLS 14.19 -- The synchronized Statement: a synchronized statement acquires
// the monitor lock on the value of an Expression (which must be a reference
// type), executes the Block, then releases the lock on normal or abrupt
// completion. This exercises a plain synchronized block, nested synchronized
// blocks on different locks, re-entrant locking on the same lock, and that the
// lock is released even when the block completes abruptly via an exception.
class SynchronizedStatement_2 {
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
        check(sum == 8);
    }
}
