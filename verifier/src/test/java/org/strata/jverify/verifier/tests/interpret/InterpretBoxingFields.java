package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A box kept in an instance field, in a static field or made by the wrapper's
 * constructor is the box that was stored: it unboxes to the stored value and
 * equals a box of that value.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_OTHER},
        exitCode = 0,
        methodsVerified = 3,
        methodsInvalid = 0,
        methodsSkipped = 0,
        errorCount = 0)
class InterpretBoxingFields {
    static class Counter {
        Integer count;
        Long total;
    }

    static Character initial;

    @SuppressWarnings("removal")
    @InterpretEntry
    static void run() {
        Counter counter = new Counter();
        counter.count = 3;
        counter.count = counter.count + 1;
        check(counter.count == 4);
        counter.total = 10L;
        counter.total += counter.count;
        check(counter.total == 14L);
        check(counter.count.equals(4) && !counter.count.equals(counter.total));

        initial = 'k';
        check(initial == 'k');

        Integer made = new Integer(5);
        check(made == 5 && made.equals(5));
    }
}
