package org.strata.jverify.langref.jls4.s4_8;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.8 -- Raw Types: the name of a generic type declaration used without any
// accompanying type arguments. Exercises constructing and using a raw parameterized
// type, where the type's members are seen at their erasures.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_8.RawTypes_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class RawTypes_2 {
    static void run() {

        // A raw type: the generic class Box_RawTypes_2<T> used without type arguments.
        // The element type T is erased to Object.
        Box_RawTypes_2 raw = new Box_RawTypes_2();
        raw.set(Integer.valueOf(7));

        // Storing a different element type is permitted for a raw type because
        // the static element type is erased to Object.
        raw.set("hello");
        Object o2 = raw.get();
        check(o2 != null);
    }
}

// Generic class; when referred to as the raw type `Box` (no type arguments),
// its members are seen at their erasures (T -> Object).
class Box_RawTypes_2<T> {
    private T element;
    private int count;

    void set(T e) {
        this.element = e;
        this.count = 1;
    }

    T get() {
        return this.element;
    }

    int size() {
        return this.count;
    }
}
