package com.aws.jverify.verifier.tests.javasupport;

import com.aws.jverify.testengine.JVerifyTest;

import com.aws.jverify.*;

import java.util.function.Function;
import java.util.function.Supplier;

import static com.aws.jverify.JVerify.*;

// Class that test the support of array allocation and accesses
@JVerifyTest(exitCode = 4, dafnyVerified = 11, dafnyErrors = 3)
class Arrays {
    
    interface SupplyArray {
        int[] supply(int x);
    }

    SupplyArray arrayConstructorReference() {
        return int[]::new;
//             ^ Error: array size might be negative
    }

    static void intArrayOfSize10() {
        int[] a = new int[10];
        a[0]=0;
        for (int i = 0; i < a.length; i++) {
            modifies(a);
            invariant(a[0]==0);
            a[i] = i;
        }
        check(a[0]==0);
    }
    
    static void nullablePointArrayOfSize10() {
        @Nullable Point [] a = new @Nullable Point[10];
        a[0]=new Point(1,2);
        for (int i = 1; i < a.length; i++) {
            modifies(a);
            // We want a better invariant like
            // invariant(Forall((Integeer j) -> Implies(0<=j && j<i,a[i]!=null && a[i].getA()==i)));
            // This does not work now as we cannot pass a non final variable in a lambda
            invariant(a[0] != null);
            invariant(a[0].getA()==1);
            a[i] = new Point(i,i+1);
        }
        check(a[0] != null);

        check(a[0].getA()==1);
    }

    static void pointArrayOfSize10() {
        Point[] a = new Point[10];
//                  ^^^^^^^^^^^^^ Error: unless an initializer is provided for the array elements, a new array of 'Point' must have empty size
        a[0]=new Point(1,2);
        for (int i = 1; i < a.length; i++) {
            modifies(a);
            // We want a better invariant like
            // invariant(Forall((Integeer j) -> Implies(0<=j && j<i,a[i]!=null && a[i].getA()==i)));
            // This does not work now as we cannot pass a non final variable in a lambda
            invariant(a[0] != null);
//                    ^^^^^^^^^^^^ Warning: the type of the other operand is a non-null type, so this comparison with 'null' will always return 'true'
            invariant(a[0].getA()==1);
            a[i] = new Point(i,i+1);
        }
        check(a[0] != null);
//            ^^^^^^^^^^^^ Warning: the type of the other operand is a non-null type, so this comparison with 'null' will always return 'true'
        check(a[0].getA()==1);
    }

    static void pointArrayOfSize0() {
        Point[] a = new Point[0];
    }

    static void intArrayOfSizeN(int n) {
        precondition(n>0);
        int[] a = new int[n];
        a[0]=0;
        int i = 0;
        for (i = 0; i < a.length; i++) {
            invariant(a[0]==0);
            modifies(a);
            a[i] = i;
        }
        check(a[0]==0);
        check(i==n);
//      ^^^^^^^^^^^ Error: assertion might not hold
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
