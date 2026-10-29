package org.strata.jverify.langref.jls4.s4_8;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.8 -- Raw Types: the name of a generic type declaration used without any
// accompanying type arguments. Exercises constructing and using a raw parameterized
// type, where the type's members are seen at their erasures.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_8.RawTypes_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class RawTypes_1 {
    static void run() {

        // A raw type: the generic class Box_RawTypes_1<T> used without type arguments.
        // The element type T is erased to Object.
        Box_RawTypes_1 raw = new Box_RawTypes_1();
        raw.set(Integer.valueOf(7));
        // Reading from a raw type yields the erasure (Object), requiring a cast.
        Object o = raw.get();
        int value = ((Integer) o).intValue();
        check(value == 7);
    }
}

// Generic class; when referred to as the raw type `Box` (no type arguments),
// its members are seen at their erasures (T -> Object).
class Box_RawTypes_1<T> {
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
