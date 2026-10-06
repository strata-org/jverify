package org.strata.jverify.langref.jls8.s8_4;

import static org.strata.jverify.JVerify.*;

// JLS 8.4.3.1 -- abstract Methods: an abstract method declared in an abstract
// class, implemented by a concrete subclass and invoked via dynamic dispatch.
class AbstractMethods {
    static void run() {
        Shape s = new Square(4);
        check(s.area() == 16);
    }
}

abstract class Shape {
    // An abstract method has no body and must be implemented by subclasses.
    abstract int area();
}

class Square extends Shape {
    private final int side;

    Square(int side) {
        this.side = side;
    }

    @Override
    int area() {
        return side * side;
    }
}
