package org.strata.jverify.langref.jls9.s9_4;

import static org.strata.jverify.JVerify.*;

// JLS 9.4.1.1 -- Overriding (by Instance Methods): a class instance method
// overrides an interface default method; dynamic dispatch selects the override.
class OverridingInstanceMethods {
    interface Greeter {
        default int value() { return 1; }
    }
    static class Impl implements Greeter {
        // Instance method overriding the interface default method.
        public int value() { return 42; }
    }
    static void run() {
        Greeter g = new Impl();
        check(g.value() == 42);
    }
}
