package org.strata.jverify.langref.jls15.s15_12;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.12.4 -- Run-Time Evaluation of Method Invocation: the target reference and the
// arguments are evaluated left to right, then for an instance method the actual method
// to run is selected by dynamic dispatch on the run-time class of the target. Here the
// static type is RteBase_RunTimeEvaluation_1 but the run-time class is RteChild_RunTimeEvaluation_1, so the overriding method
// runs.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_12.RunTimeEvaluation_1#run")
class RunTimeEvaluation_1 {
    static int counter = 0;

    static int next() {
        counter = counter + 1;
        return counter;
    }

    static void run() {

        // Dynamic dispatch: static type RteBase_RunTimeEvaluation_1, run-time class RteChild_RunTimeEvaluation_1.
        RteBase_RunTimeEvaluation_1 obj = new RteChild_RunTimeEvaluation_1();
        int dispatched = obj.value();
        check(dispatched == 99);
    }

    // Encodes its two arguments as a two-digit number to observe evaluation order.
    static int order(int first, int second) {
        return first * 10 + second;
    }
}

class RteBase_RunTimeEvaluation_1 {
    int value() { return 1; }
}

class RteChild_RunTimeEvaluation_1 extends RteBase_RunTimeEvaluation_1 {
    @Override
    int value() { return 99; }
}
