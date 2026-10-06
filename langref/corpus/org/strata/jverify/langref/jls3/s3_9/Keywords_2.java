package org.strata.jverify.langref.jls3.s3_9;

import static org.strata.jverify.JVerify.*;

// JLS 3.9 -- Keywords: exercises a representative set of reserved keywords
// (control flow: if/else/for/while/do/switch/case/default/break/continue/return,
//  types/literals: int/long/boolean/true/false, operators: instanceof/new,
//  modifiers exercised on the class/method, var as a reserved local type name).
class Keywords_2 {
    static void run() {

        // 'while', 'do'
        int w = 0;
        while (w < 3) {
            w++;
        }
        check(w == 3);
    }

    static int helper(int n) {
        return n * n;
    }
}
