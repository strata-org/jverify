package org.strata.jverify.langref.jls6.s6_4;

import static org.strata.jverify.JVerify.*;

// JLS 6.4.1 -- Shadowing: even when a local variable shadows an instance
// field, the shadowed field remains accessible via `this.<name>`.
class Shadowing_2 {
    static void run() {
        check(new Holder2().m() == 1);
    }
}

class Holder2 {
    int x = 1;

    int m() {
        int x = 2; // shadows the field
        return this.x; // still reaches the field via `this`
    }
}
