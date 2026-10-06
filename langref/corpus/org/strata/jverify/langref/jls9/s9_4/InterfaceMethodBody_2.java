package org.strata.jverify.langref.jls9.s9_4;

import static org.strata.jverify.JVerify.*;

// JLS 9.4.3 -- Interface Method Body: default, static, and private methods of an
// interface each have a body; abstract methods do not. Exercises all three bodies.
class InterfaceMethodBody_2 {
    interface Calc {
        int base();                       // abstract: no body
        static int unit() { return 10; }  // static: has a body
        private int helper() {            // private: has a body
            return base() + 1;
        }
        default int compute() {           // default: has a body, calls private + static
            return helper() + unit();
        }
    }
    static class C implements Calc {
        public int base() { return 5; }
    }
    static void run() {

        Calc c = new C();
   // (5 + 1) + 10
        check(Calc.unit() == 10);
    }
}
