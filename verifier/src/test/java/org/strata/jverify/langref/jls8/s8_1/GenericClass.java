package org.strata.jverify.langref.jls8.s8_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.1.2 -- Generic Classes and Type Parameters: a class with a type
// parameter, instantiated at a concrete type argument.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_1.GenericClass#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class GenericClass {
    static void run() {
        Box<Integer> b = new Box<>(7);
        int v = b.get();
        check(v == 7);
    }
}

class Box<T> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
