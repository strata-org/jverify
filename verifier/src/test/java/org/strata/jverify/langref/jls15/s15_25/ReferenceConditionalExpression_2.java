package org.strata.jverify.langref.jls15.s15_25;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.25.3 -- Reference Conditional Expressions: ? : where the operands are reference types,
// the result type is the least upper bound (lub) of the two operand types.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_25.ReferenceConditionalExpression_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_ASSERT_FAILED})
class ReferenceConditionalExpression_2 {
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
        check(a.legs() == 4);
    }
}
