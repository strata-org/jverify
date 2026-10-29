package org.strata.jverify.langref.jls8.s8_8;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.8.8 -- Constructor Overloading: multiple constructors distinguished by their parameter signatures, resolved by overload resolution.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_8.ConstructorOverloading_2#run")
class ConstructorOverloading_2 {
    final int value;

    ConstructorOverloading_2() {
        this.value = 0;
    }

    ConstructorOverloading_2(int v) {
        this.value = v;
    }

    ConstructorOverloading_2(int a, int b) {
        this.value = a + b;
    }

    static void run() {

        ConstructorOverloading_2 c0 = new ConstructorOverloading_2();
        ConstructorOverloading_2 c1 = new ConstructorOverloading_2(7);
        ConstructorOverloading_2 c2 = new ConstructorOverloading_2(4, 5);
        check(c1.value == 7);
    }
}
