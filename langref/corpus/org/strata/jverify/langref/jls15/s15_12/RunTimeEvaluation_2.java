package org.strata.jverify.langref.jls15.s15_12;

import static org.strata.jverify.JVerify.*;

// JLS 15.12.4 -- Run-Time Evaluation of Method Invocation: the target reference and the
// arguments are evaluated left to right, then for an instance method the actual method
// to run is selected by dynamic dispatch on the run-time class of the target. Here the
// static type is RteBase_RunTimeEvaluation_2 but the run-time class is RteChild_RunTimeEvaluation_2, so the overriding method
// runs.
class RunTimeEvaluation_2 {
    static int counter = 0;

    static int next() {
        counter = counter + 1;
        return counter;
    }

    static void run() {

        int ordered = order(next(), next());
        check(ordered == 12);
    }

    // Encodes its two arguments as a two-digit number to observe evaluation order.
    static int order(int first, int second) {
        return first * 10 + second;
    }
}

class RteBase_RunTimeEvaluation_2 {
    int value() { return 1; }
}

class RteChild_RunTimeEvaluation_2 extends RteBase_RunTimeEvaluation_2 {
    @Override
    int value() { return 99; }
}
