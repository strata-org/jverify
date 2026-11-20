package org.strata.jverify.langref.jls9.s9_6;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 9.6.4.4 -- @Override: a method annotated @Override must override (or implement)
// a method declared in a supertype; here a subclass overrides a superclass method.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls9.s9_6.OverrideAnnotation#run")
class OverrideAnnotation {
    static class Base {
        int value() {
            return 1;
        }
    }

    static class Derived extends Base {
        @Override
        int value() {
            return 2;
        }
    }

    static void run() {
        Base b = new Derived();
        check(b.value() == 2);
    }
}
