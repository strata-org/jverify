package org.strata.jverify.langref.jls14.s14_11;

import static org.strata.jverify.JVerify.*;

// JLS 14.11.2 -- switch with arrow rules: 'case L ->' executes exactly the rule's body
// with no fallthrough; the body may be an expression, a block, or a throw statement.
class SwitchArrowRules_3 {
    static void run() {

        // default arrow rule taken when nothing else matches
        int n = 42;
        int bucket = -1;
        switch (n) {
            case 0 -> bucket = 0;
            default -> bucket = 9;
        }
        check(bucket == 9);
    }
}
