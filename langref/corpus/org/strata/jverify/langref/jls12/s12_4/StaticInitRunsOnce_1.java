package org.strata.jverify.langref.jls12.s12_4;

import static org.strata.jverify.JVerify.*;

// JLS 12.4.1 -- When Initialization Occurs: a class is initialized at most
// once; subsequent accesses do not re-run its static initializer.
class StaticInitRunsOnce_1 {
    static void run() {
        int first = C_SIRO1.count;
        int second = C_SIRO1.count; // does not re-run the static initializer
        check(first == 1 && second == 1);
    }
}

class C_SIRO1 {
    static int count;
    static {
        count++;
    }
}
