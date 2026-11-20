package org.strata.jverify.langref.jls8.s8_8;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.8.7 -- Constructor Body: a constructor body containing statements (local variables, loops, field initialization) with no explicit constructor invocation.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_8.ConstructorBody#run")
class ConstructorBody {
    int factorial;

    ConstructorBody(int n) {
        int acc = 1;
        for (int i = 2; i <= n; i++) {
            acc *= i;
        }
        this.factorial = acc;
    }

    static void run() {
        ConstructorBody c = new ConstructorBody(5);
        check(c.factorial == 120);
    }
}
