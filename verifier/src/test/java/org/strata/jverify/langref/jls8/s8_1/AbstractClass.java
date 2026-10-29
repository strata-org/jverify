package org.strata.jverify.langref.jls8.s8_1;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.1.1.1 -- abstract Classes: an abstract class cannot be instantiated,
// but a concrete subclass implementing the abstract method can.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_1.AbstractClass#run")
class AbstractClass {
    static void run() {
        AbstractShape s = new Square(4);
        check(s.area() == 16);
    }
}

abstract class AbstractShape {
    abstract int area();
}

class Square extends AbstractShape {
    private final int side;

    Square(int side) {
        this.side = side;
    }

    @Override
    int area() {
        return side * side;
    }
}
