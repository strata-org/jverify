package org.strata.jverify.langref.jls4.s4_9;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.9 -- Intersection Types: an intersection type T1 & ... & Tn appears in the
// bound of a type parameter (<T extends A & B>). A value of such a parameter type is
// usable as every member of the intersection. Here T is constrained to implement both
// Named and Sized, and the method calls each interface's member through the one value.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_9.IntersectionTypes#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class IntersectionTypes {
    static void run() {
        Widget w = new Widget();
        // describe is generic with an intersection bound T extends Named & Sized,
        // so w (which implements both) can be passed and used as both interfaces.
        int r = describe(w);
        check(r == 7); // size() == 4 plus name length 3 ("box")
    }

    // Type parameter with an intersection type bound: Named & Sized.
    static <T extends Named & Sized> int describe(T t) {
        // t is usable as a Named ...
        int nameLen = t.name().length();
        // ... and as a Sized, within the same value.
        int sz = t.size();
        return nameLen + sz;
    }

    interface Named {
        String name();
    }

    interface Sized {
        int size();
    }

    static class Widget implements Named, Sized {
        public String name() { return "box"; }
        public int size() { return 4; }
    }
}
