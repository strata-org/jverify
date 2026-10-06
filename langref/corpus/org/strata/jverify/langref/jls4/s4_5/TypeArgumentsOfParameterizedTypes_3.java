package org.strata.jverify.langref.jls4.s4_5;

import static org.strata.jverify.JVerify.*;

// JLS 4.5.1 -- Type Arguments of Parameterized Types: a generic type instantiated
// with concrete type arguments and with a wildcard type argument.
class TypeArgumentsOfParameterizedTypes_3 {
    static void run() {

        // Parameterized type Box<Integer> -- concrete reference type argument.
        Box<Integer> bi = new Box<Integer>(42);

        // Different parameterization Box<Box<Integer>> -- nested type argument.
        Box<Box<Integer>> bbi = new Box<Box<Integer>>(bi);

        // Wildcard type argument: Box<?> matches any parameterization.
        Box<?> any = bi;
        check(any != null);
    }

    static class Box<T> {
        private final T value;
        Box(T value) { this.value = value; }
        T get() { return value; }
    }
}
