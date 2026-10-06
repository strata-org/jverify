package org.strata.jverify.langref.jls8.s8_8;

import static org.strata.jverify.JVerify.*;

// JLS 8.8.10 -- Preventing Instantiation: a class with only a private constructor cannot be instantiated externally; instances are produced via a static factory.
class PreventingInstantiation {
    final int n;

    private PreventingInstantiation(int n) {
        this.n = n;
    }

    static PreventingInstantiation of(int n) {
        return new PreventingInstantiation(n);
    }

    static void run() {
        PreventingInstantiation p = PreventingInstantiation.of(99);
        check(p.n == 99);
    }
}
