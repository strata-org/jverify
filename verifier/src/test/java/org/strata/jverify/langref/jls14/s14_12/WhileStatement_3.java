package org.strata.jverify.langref.jls14.s14_12;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.12 -- The while Statement: evaluates a boolean condition before each
// iteration and executes the body while it is true; covers a normal loop, a
// loop whose condition is initially false (body never runs), and abrupt
// completion of the body via break and continue.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_12.WhileStatement_3#run")
class WhileStatement_3 {
    static void run() {
      // loop exits when condition becomes false

        // Condition initially false: body executes zero times.
        int never = 0;
        while (never > 0) {
            never = 100;    // unreachable at runtime
        }
        check(never == 0);
    }
}
