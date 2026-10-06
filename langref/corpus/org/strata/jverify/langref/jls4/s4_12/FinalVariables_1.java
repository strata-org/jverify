package org.strata.jverify.langref.jls4.s4_12;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.4 -- final Variables: a final variable may be assigned exactly once.
// Covers a blank final assigned in the constructor, a final local, a final
// local assigned exactly once (definite assignment), and a constant variable.
class FinalVariables_1 {
    static final int CONSTANT = 99;     // constant variable
    final int blankFinal;               // blank final, set in constructor

    FinalVariables_1(int v) {
        this.blankFinal = v;            // single assignment of blank final
    }

    static void run() {

        final int x = 10;

        check(x == 10);
    }
}
