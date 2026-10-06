package org.strata.jverify.langref.jls9.s9_1;

import static org.strata.jverify.JVerify.*;

// JLS 9.1.2 -- Generic Interfaces and Type Parameters: a generic interface
// declared with a type parameter, implemented by a class and invoked.
class GenericInterfaces {
    static void run() {
        Box<Integer> b = new IntBox(7);
        int v = b.get();
        check(v == 7);
    }
}

interface Box<T> {
    T get();
}

class IntBox implements Box<Integer> {
    private final int value;
    IntBox(int value) { this.value = value; }
    public Integer get() { return value; }
}
