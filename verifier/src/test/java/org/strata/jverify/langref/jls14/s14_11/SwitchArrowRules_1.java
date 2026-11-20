package org.strata.jverify.langref.jls14.s14_11;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.11.2 -- switch with arrow rules: 'case L ->' executes exactly the rule's body
// with no fallthrough; the body may be an expression, a block, or a throw statement.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_11.SwitchArrowRules_1#run")
class SwitchArrowRules_1 {
    static void run() {

        int selector = 2;
        int result = -1;
        // arrow rule with single-expression-statement bodies; no fallthrough
        switch (selector) {
            case 1 -> result = 10;
            case 2 -> result = 20;
            case 3 -> result = 30;
            default -> result = 0;
        }
        check(result == 20);
    }
}
