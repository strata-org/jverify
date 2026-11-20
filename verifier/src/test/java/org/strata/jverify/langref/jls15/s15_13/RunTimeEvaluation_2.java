package org.strata.jverify.langref.jls15.s15_13;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.13.3 -- Run-Time Evaluation of Method References: a method reference expression
// is evaluated to produce a reference to an instance of a functional interface; the
// target method is invoked when the interface method is later called. This exercises
// that the bound receiver (Primary :: Identifier) is captured at evaluation time and the
// produced instance behaves like the referenced method.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_13.RunTimeEvaluation_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class RunTimeEvaluation_2 {
    interface IntSupplier {
        int get();
    }

    interface IntOp {
        int apply(int v);
    }

    static class Counter {
        int base;

        Counter(int base) {
            this.base = base;
        }

        int read() {
            return base;
        }

        int addTo(int v) {
            return base + v;
        }
    }

    static void run() {

        // Evaluating a bound method reference captures the current receiver object.
        Counter c = new Counter(10);

        // An unbound (arbitrary-object) reference picks the receiver from the argument
        // at each invocation.
        IntOp addToCounter = c::addTo;
        check(addToCounter.apply(5) == 15);
    }
}
