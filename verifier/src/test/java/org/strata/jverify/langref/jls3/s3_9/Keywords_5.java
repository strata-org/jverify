package org.strata.jverify.langref.jls3.s3_9;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.9 -- Keywords: exercises a representative set of reserved keywords
// (control flow: if/else/for/while/do/switch/case/default/break/continue/return,
//  types/literals: int/long/boolean/true/false, operators: instanceof/new,
//  modifiers exercised on the class/method, var as a reserved local type name).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_9.Keywords_5#run")
class Keywords_5 {
    static void run() {

        boolean flag = true;

        // 'long' type, ternary using boolean keywords
        long big = flag ? 42L : 0L;
        check(big == 42L);
    }

    static int helper(int n) {
        return n * n;
    }
}
