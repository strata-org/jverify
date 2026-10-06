package org.strata.jverify.langref.jls8.s8_1;

import static org.strata.jverify.JVerify.*;

// JLS 8.1.1.1 -- abstract Classes: an abstract class cannot be instantiated,
// but a concrete subclass implementing the abstract method can.
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
