package org.strata.jverify.langref.jls15.s15_25;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.25.3 -- Reference Conditional Expressions: ? : where the operands are reference types,
// the result type is the least upper bound (lub) of the two operand types.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_25.ReferenceConditionalExpression_1#run")
class ReferenceConditionalExpression_1 {
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
        check(picked == c);
    }
}
