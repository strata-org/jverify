package org.strata.jverify.langref.jls15.s15_9;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.9.5 -- Anonymous Class Declarations: a class instance creation
// expression with a class body declares an anonymous class implementing
// an interface, capturing an enclosing local variable.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_9.AnonymousClassDeclaration#run")
class AnonymousClassDeclaration {
    static void run() {
        final int base = 10;
        Adder a = new Adder() {
            public int add(int v) {
                return base + v;
            }
        };
        check(a.add(5) == 15);
    }
}

interface Adder {
    int add(int v);
}
