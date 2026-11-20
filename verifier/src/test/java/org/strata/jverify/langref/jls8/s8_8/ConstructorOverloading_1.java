package org.strata.jverify.langref.jls8.s8_8;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.8.8 -- Constructor Overloading: multiple constructors distinguished by their parameter signatures, resolved by overload resolution.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_8.ConstructorOverloading_1#run")
class ConstructorOverloading_1 {
    final int value;

    ConstructorOverloading_1() {
        this.value = 0;
    }

    ConstructorOverloading_1(int v) {
        this.value = v;
    }

    ConstructorOverloading_1(int a, int b) {
        this.value = a + b;
    }

    static void run() {

        ConstructorOverloading_1 c0 = new ConstructorOverloading_1();
        ConstructorOverloading_1 c1 = new ConstructorOverloading_1(7);
        ConstructorOverloading_1 c2 = new ConstructorOverloading_1(4, 5);
        check(c0.value == 0);
    }
}
