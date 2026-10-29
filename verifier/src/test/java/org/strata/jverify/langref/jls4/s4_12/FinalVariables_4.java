package org.strata.jverify.langref.jls4.s4_12;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.4 -- final Variables: a final variable may be assigned exactly once.
// Covers a blank final assigned in the constructor, a final local, a final
// local assigned exactly once (definite assignment), and a constant variable.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_12.FinalVariables_4#run")
class FinalVariables_4 {
    static final int CONSTANT = 99;     // constant variable
    final int blankFinal;               // blank final, set in constructor

    FinalVariables_4(int v) {
        this.blankFinal = v;            // single assignment of blank final
    }

    static void run() {
                         // assigned exactly once

        FinalVariables_4 obj = new FinalVariables_4(7);
        check(obj.blankFinal == 7);
    }
}
