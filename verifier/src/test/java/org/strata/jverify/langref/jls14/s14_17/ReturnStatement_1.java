package org.strata.jverify.langref.jls14.s14_17;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.17 -- The return Statement: both forms, "return;" (no value,
// transfers control out of a void method) and "return Expression;"
// (value-returning), each terminating the method abruptly.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_17.ReturnStatement_1#run")
class ReturnStatement_1 {
    // "return Expression;" -- value-returning return.
    static int doubled(int n) {
        return n * 2;
    }

    // "return;" -- a return statement with no Expression in a void method,
    // used to abruptly exit early before reaching the end of the body.
    static void clamp(int[] out, int v) {
        if (v < 0) {
            out[0] = 0;
            return;            // early exit, no value
        }
        out[0] = v;
    }

    static void run() {

        int r = doubled(21);
        check(r == 42);
    }
}
