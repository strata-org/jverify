package org.strata.jverify.langref.jls15.s15_25;

import static org.strata.jverify.JVerify.*;

// JLS 15.25.3 -- Reference Conditional Expressions: ? : where the operands are reference types,
// the result type is the least upper bound (lub) of the two operand types.
class ReferenceConditionalExpression_3 {
    static class Animal {
        int legs() { return 4; }
    }

    static class Cat extends Animal {
        @Override int legs() { return 4; }
    }

    static class Bird extends Animal {
        @Override int legs() { return 2; }
    }

    static void run() {

        // Same reference type on both branches.
        Cat c = new Cat();
        Cat other = new Cat();
        Cat picked = true ? c : other;

        // Operands of different subtypes: result type is the lub (Animal here).
        Animal a = (3 > 1) ? new Cat() : new Bird();

        Animal b = (3 < 1) ? new Cat() : new Bird();
        check(b.legs() == 2);
    }
}
