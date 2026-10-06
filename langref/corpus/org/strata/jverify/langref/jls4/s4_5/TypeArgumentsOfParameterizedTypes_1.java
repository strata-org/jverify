package org.strata.jverify.langref.jls4.s4_5;

import static org.strata.jverify.JVerify.*;

// JLS 4.5.1 -- Type Arguments of Parameterized Types: a generic type instantiated
// with concrete type arguments and with a wildcard type argument.
class TypeArgumentsOfParameterizedTypes_1 {
    static void run() {

        // Parameterized type Box<Integer> -- concrete reference type argument.
        Box<Integer> bi = new Box<Integer>(42);
        check(bi.get() == 42);
    }

    static class Box<T> {
        private final T value;
        Box(T value) { this.value = value; }
        T get() { return value; }
    }
}
