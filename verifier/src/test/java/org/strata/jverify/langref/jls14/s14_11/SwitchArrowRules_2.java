package org.strata.jverify.langref.jls14.s14_11;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.11.2 -- switch with arrow rules: 'case L ->' executes exactly the rule's body
// with no fallthrough; the body may be an expression, a block, or a throw statement.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_11.SwitchArrowRules_2#run")
class SwitchArrowRules_2 {
    static void run() {

        // arrow rule with block body and a comma-separated label
        int day = 7;
        int kind = -1;
        switch (day) {
            case 1, 2, 3, 4, 5 -> {
                kind = 1;
            }
            case 6, 7 -> {
                kind = 2;
            }
            default -> kind = 0;
        }
        check(kind == 2);
    }
}
