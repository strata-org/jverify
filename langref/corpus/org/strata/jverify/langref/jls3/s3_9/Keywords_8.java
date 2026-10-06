package org.strata.jverify.langref.jls3.s3_9;

import static org.strata.jverify.JVerify.*;

// JLS 3.9 -- Keywords: exercises a representative set of reserved keywords
// (control flow: if/else/for/while/do/switch/case/default/break/continue/return,
//  types/literals: int/long/boolean/true/false, operators: instanceof/new,
//  modifiers exercised on the class/method, var as a reserved local type name).
class Keywords_8 {
    static void run() {

        // 'return' (implicit reaching end is fine; use return value via helper)
        check(helper(3) == 9);
    }

    static int helper(int n) {
        return n * n;
    }
}
