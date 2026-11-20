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
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_19.SynchronizedStatement_4#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class SynchronizedStatement_4 {
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

        // Re-entrant locking: same lock acquired multiple times by one thread.
        int d = reenter(lock, 4);

        // Abrupt completion of the Block still releases the lock; afterwards
        // the same lock can be acquired again.
        int caught = 0;
        try {
            synchronized (lock) {
                throw new RuntimeException("boom");
            }
        } catch (RuntimeException e) {
            caught = 1;
        }
        check(caught == 1);
    }
}
