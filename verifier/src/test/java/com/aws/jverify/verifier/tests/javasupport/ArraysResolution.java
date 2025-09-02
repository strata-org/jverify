package com.aws.jverify.verifier.tests.javasupport;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 22)
class ArraysResolution {

    static void pointArrayOfSize10() {
        Point[] a = new Point[10];
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: type parameter (T) passed to type GhostArray must be nonempty (got Point)
        var zeroPoint = new Point(1,2);
        a[0] = zeroPoint;
        for (int i = 1; i < a.length; i++) {
            modifies(a);
            // We want a better invariant like
            // invariant(Forall((Integeer j) -> Implies(0<=j && j<i,a[i]!=null && a[i].getA()==i)));
            // This does not work now as we cannot pass a non final variable in a lambda
            invariant(a[0] != null);
//                    ^^^^^^^^^^^^ Warning: the type of the other operand is a non-null type, so this comparison with 'null' will always return 'true'
            invariant(a[0].getA()==1);
            invariant(a[0] == zeroPoint); // should not be necessary
            var loopPoint = new Point(i,i+1);
            a[i] = loopPoint;
        }
        check(a[0] != null);
//            ^^^^^^^^^^^^ Warning: the type of the other operand is a non-null type, so this comparison with 'null' will always return 'true'
        check(a[0].getA()==1);
    }

    static void pointArrayOfSize0() {
        Point[] a = new Point[0];
//      ^^^^^^^^^^^^^^^^^^^^^^^^^ Error: type parameter (T) passed to type GhostArray must be nonempty (got Point)
    }

    static class Point {
        private int a;
        private int b;

        public Point(int a_, int b_) {
            postcondition(this.a == a_);
            postcondition(this.b == b_);
            this.a = a_;
            this.b = b_;
        }

        @Pure
        public int getA() {
            reads(this);
            return a;
        }
    }
}
