package org.strata.jverify.langref.jls15.s15_13;

import static org.strata.jverify.JVerify.*;

// JLS 15.13.1 -- Compile-Time Declaration of a Method Reference: exercises the four
// forms of method reference (static, instance-of-particular-object, instance-of-arbitrary-object,
// and constructor) so the compiler resolves a distinct compile-time declaration for each.
class CompileTimeDeclaration_5 {
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
        IntUnary stat = CompileTimeDeclaration_5::twice;

        // Primary :: Identifier  -- reference to instance method of a particular object (bound).
        Box particular = new Box(7);
        BoundGetter bound = particular::get;

        // ReferenceType :: Identifier  -- instance method of an arbitrary object of the type;
        // the first functional-interface parameter becomes the receiver.
        BoxUnary arbitrary = Box::get;

        // A binary static-method reference resolves to Integer.sum(int,int).
        IntBinary sum = Integer::sum;
        check(sum.apply(4, 6) == 10);
    }

    interface BoundGetter {
        int get();
    }
}
