package org.strata.jverify.langref.jls8.s8_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.1.6 -- Permitted Direct Subclasses: the "permits" clause of a sealed
// class enumerates exactly the classes allowed to directly extend it.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_1.PermittedSubclasses#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class PermittedSubclasses {
    static void run() {
        Expr e = new Add(new Lit(3), new Lit(4));
        check(eval(e) == 7);
    }

    static int eval(Expr e) {
        if (e instanceof Lit l) {
            return l.value();
        }
        Add a = (Add) e;
        return eval(a.left()) + eval(a.right());
    }
}

sealed interface Expr permits Lit, Add {
}

record Lit(int value) implements Expr {
}

record Add(Expr left, Expr right) implements Expr {
}
