package org.strata.jverify.langref.jls14.s14_12;

import static org.strata.jverify.JVerify.*;

// JLS 14.12 -- The while Statement: evaluates a boolean condition before each
// iteration and executes the body while it is true; covers a normal loop, a
// loop whose condition is initially false (body never runs), and abrupt
// completion of the body via break and continue.
class WhileStatement_2 {
    static void run() {

        // Basic counting loop: condition checked before each iteration.
        int sum = 0;
        int i = 1;
        while (i <= 4) {
            sum += i;
            i++;
        }   // 1+2+3+4
        check(i == 5);
    }
}
