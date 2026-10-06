package org.strata.jverify.langref.jls3.s3_9;

import static org.strata.jverify.JVerify.*;

// JLS 3.9 -- Keywords: exercises a representative set of reserved keywords
// (control flow: if/else/for/while/do/switch/case/default/break/continue/return,
//  types/literals: int/long/boolean/true/false, operators: instanceof/new,
//  modifiers exercised on the class/method, var as a reserved local type name).
class Keywords_1 {
    static void run() {

        // 'int', 'final', 'var' (reserved type name), assignment of literals 'true'/'false'
        final int base = 10;
        var sum = 0;

        // 'for', 'continue', 'break'
        for (int i = 0; i < base; i++) {
            if (i == 5) {
                continue;
            }
            if (i > 7) {
                break;
            }
            sum += i;
        }
        // i = 0,1,2,3,4,(5 skipped),6,7 -> 0+1+2+3+4+6+7 = 23
        check(sum == 23);
    }

    static int helper(int n) {
        return n * n;
    }
}
