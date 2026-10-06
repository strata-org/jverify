package org.strata.jverify.langref.jls8.s8_1;

import static org.strata.jverify.JVerify.*;

// JLS 8.1.2 -- Generic Classes and Type Parameters: a class with a type
// parameter, instantiated at a concrete type argument.
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
