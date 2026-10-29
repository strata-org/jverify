package org.strata.jverify.langref.jls8.s8_8;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.8.7.1 -- Explicit Constructor Invocations: alternate constructor invocation this(...) and superclass invocation super(...).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_8.ExplicitConstructorInvocation_1#run")
class ExplicitConstructorInvocation_1 extends ECIBase_ExplicitConstructorInvocation_1 {
    final int y;

    ExplicitConstructorInvocation_1(int x, int y) {
        super(x);          // explicit superclass constructor invocation
        this.y = y;
    }

    ExplicitConstructorInvocation_1(int x) {
        this(x, x * 2);    // alternate constructor invocation
    }

    static void run() {

        ExplicitConstructorInvocation_1 e = new ExplicitConstructorInvocation_1(3);
        check(e.getX() == 3);
    }
}

class ECIBase_ExplicitConstructorInvocation_1 {
    private final int x;

    ECIBase_ExplicitConstructorInvocation_1(int x) {
        this.x = x;
    }

    int getX() {
        return x;
    }
}
