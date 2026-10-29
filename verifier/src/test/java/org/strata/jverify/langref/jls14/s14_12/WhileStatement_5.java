package org.strata.jverify.langref.jls14.s14_12;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.12 -- The while Statement: evaluates a boolean condition before each
// iteration and executes the body while it is true; covers a normal loop, a
// loop whose condition is initially false (body never runs), and abrupt
// completion of the body via break and continue.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_12.WhileStatement_5#run")
class WhileStatement_5 {
    static void run() {

        // continue transfers control to re-evaluate the condition.
        int count = 0;
        int k = 0;
        while (k < 6) {
            k++;
            if (k % 2 == 0) {
                continue;   // skip the accumulation for even k
            }
            count++;
        }
        check(count == 3);
    }
}
