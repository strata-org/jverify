package org.strata.jverify.langref.jls6.s6_4;

import static org.strata.jverify.JVerify.*;

// JLS 6.4.1 -- Shadowing: a field declared in an inner class shadows a field
// with the same simple name declared in the enclosing class within the inner
// class's body.
class Shadowing_4 {
    static void run() {
        Outer4 o = new Outer4();
        Outer4.Inner4 i = o.new Inner4();
        check(i.m() == 2);
    }
}

class Outer4 {
    int x = 1;

    class Inner4 {
        int x = 2; // shadows Outer4.this.x within this scope

        int m() {
            return x;
        }
    }
}
