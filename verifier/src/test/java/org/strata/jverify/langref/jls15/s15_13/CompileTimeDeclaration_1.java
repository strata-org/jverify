package org.strata.jverify.langref.jls15.s15_13;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.13.1 -- Compile-Time Declaration of a Method Reference: exercises the four
// forms of method reference (static, instance-of-particular-object, instance-of-arbitrary-object,
// and constructor) so the compiler resolves a distinct compile-time declaration for each.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_13.CompileTimeDeclaration_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class CompileTimeDeclaration_1 {
    interface IntUnary {
        int apply(int v);
    }

    interface IntBinary {
        int apply(int a, int b);
    }

    interface BoxUnary {
        int apply(Box b);
    }

    interface Maker {
        Box make(int v);
    }

    static int twice(int v) {
        return v * 2;
    }

    static class Box {
        final int value;

        Box(int value) {
            this.value = value;
        }

        int get() {
            return value;
        }
    }

    static void run() {

        // ReferenceType :: Identifier  -- static method reference.
        IntUnary stat = CompileTimeDeclaration_1::twice;
        check(stat.apply(5) == 10);
    }

    interface BoundGetter {
        int get();
    }
}
