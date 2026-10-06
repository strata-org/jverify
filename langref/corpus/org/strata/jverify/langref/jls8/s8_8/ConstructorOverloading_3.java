package org.strata.jverify.langref.jls8.s8_8;

import static org.strata.jverify.JVerify.*;

// JLS 8.8.8 -- Constructor Overloading: multiple constructors distinguished by their parameter signatures, resolved by overload resolution.
class ConstructorOverloading_3 {
    final int value;

    ConstructorOverloading_3() {
        this.value = 0;
    }

    ConstructorOverloading_3(int v) {
        this.value = v;
    }

    ConstructorOverloading_3(int a, int b) {
        this.value = a + b;
    }

    static void run() {

        ConstructorOverloading_3 c0 = new ConstructorOverloading_3();
        ConstructorOverloading_3 c1 = new ConstructorOverloading_3(7);
        ConstructorOverloading_3 c2 = new ConstructorOverloading_3(4, 5);
        check(c2.value == 9);
    }
}
