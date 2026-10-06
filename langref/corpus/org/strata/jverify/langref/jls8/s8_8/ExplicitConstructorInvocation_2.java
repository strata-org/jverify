package org.strata.jverify.langref.jls8.s8_8;

import static org.strata.jverify.JVerify.*;

// JLS 8.8.7.1 -- Explicit Constructor Invocations: alternate constructor invocation this(...) and superclass invocation super(...).
class ExplicitConstructorInvocation_2 extends ECIBase_ExplicitConstructorInvocation_2 {
    final int y;

    ExplicitConstructorInvocation_2(int x, int y) {
        super(x);          // explicit superclass constructor invocation
        this.y = y;
    }

    ExplicitConstructorInvocation_2(int x) {
        this(x, x * 2);    // alternate constructor invocation
    }

    static void run() {

        ExplicitConstructorInvocation_2 e = new ExplicitConstructorInvocation_2(3);

        check(e.y == 6);
    }
}

class ECIBase_ExplicitConstructorInvocation_2 {
    private final int x;

    ECIBase_ExplicitConstructorInvocation_2(int x) {
        this.x = x;
    }

    int getX() {
        return x;
    }
}
