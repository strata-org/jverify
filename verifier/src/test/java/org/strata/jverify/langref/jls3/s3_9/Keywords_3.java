package org.strata.jverify.langref.jls3.s3_9;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.9 -- Keywords: exercises a representative set of reserved keywords
// (control flow: if/else/for/while/do/switch/case/default/break/continue/return,
//  types/literals: int/long/boolean/true/false, operators: instanceof/new,
//  modifiers exercised on the class/method, var as a reserved local type name).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_9.Keywords_3#run")
class Keywords_3 {
    static void run() {

        int d = 0;
        do {
            d++;
        } while (d < 2);
        check(d == 2);
    }

    static int helper(int n) {
        return n * n;
    }
}
