// ^ /object-contract.java(109:22-109:31) Related location: this is the precondition that could not be proved
package com.aws.jverify.verifier.tests.javasupport;

import com.aws.jverify.testengine.JVerifyTest;

import com.aws.jverify.*;

import static com.aws.jverify.JVerify.*;

// Class that test the support of array allocation and accesses
@JVerifyTest(exitCode = 4, methodsVerified = 6, failedAssertions = 2)
class ArraysVerification {
    
    interface SupplyArray {
        int[] supply(int x);
    }

    SupplyArray arrayConstructorReference() {
        return int[]::new;
//             ^ Error: a precondition for this call could not be proved
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

    static void arrayCopy() {
        int[] tab = new int[4];
        int[] tab2 = new int[4];
        tab2[1] = 10;
        tab[1] = tab2[1];
        check(tab[1]==10);
    }
    
    static void nullablePointArrayOfSize10() {
        @Nullable Point [] a = new @Nullable Point[10];
        
        var zeroPoint = new Point(1,2);
        a[0] = zeroPoint;
        for (int i = 1; i < a.length; i++) {
            modifies(a);
            // We want a better invariant like
            // invariant(Forall((Integeer j) -> Implies(0<=j && j<i,a[i]!=null && a[i].getA()==i)));
            // This does not work now as we cannot pass a non final variable in a lambda
            invariant(a[0] != null);
            invariant(a[0].getA()==1);
            invariant(a[0] == zeroPoint);

            var loopPoint = new Point(i,i+1);
            a[i] = loopPoint;
        }
        check(a[0] != null);

        check(a[0].getA()==1);
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
//      ^^^^^^^^^^^ Error: assertion could not be proved
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
