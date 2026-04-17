package com.aws.jverify.verifier.tests.javasupport.expressions;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

interface DummyInterface {}
class DummyClass implements DummyInterface {
    public static DummyInterface create() {
        DummyInterface t =  new DummyClass(); return t; }
}
class DummyClass2 implements DummyInterface {
    public static DummyInterface create() {DummyInterface t =  new DummyClass2(); return t;}
}


@JVerifyTest(skip = "Strata: not yet supported", exitCode = 4, methodsVerified = 19, errorCount = 13)
class Operators {
    static void Plus() {
        int x = 3;
        int y = 4;
        int z = x + y;
        check(z==7);
    }

    static void PlusKO() {
        int x = 3;
        int y = 4;
        int z = x + y;
        check(z==6);
//      ^^^^^^^^^^^ Error: assertion does not hold
    }

    static void Minus() {
        int x = 3;
        int y = 4;
        int z = y - x;
        check(z==1);
    }

    static void MinusKO() {
        int x = 3;
        int y = 4;
        int z = y - x;
        check(z==0);
//      ^^^^^^^^^^^ Error: assertion does not hold
    }

    static void Mult() {
        int x = 3;
        int y = 4;
        int z = x * y;
        check(z==12);
    }

    static void MultKO() {
        int x = 3;
        int y = 4;
        int z = x * y;
        check(z==11);
//      ^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void Mod() {
        int x = 3;
        int y = 23;
        int z = y % x;
        check(z==2);
    }

    static void ModKO() {
        int x = 3;
        int y = 23;
        int z = y % x;
        check(z==1);
//      ^^^^^^^^^^^ Error: assertion does not hold
    }

    static void Gt() {
        int x = 3;
        int y = 23;
        check(y > x);
    }

    static void GtKO() {
        int x = 33;
        int y = 23;
        check(y > x);
//      ^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void Lt() {
        int x = 3;
        int y = 23;
        check(x < y);
    }

    static void LtKO() {
        int x = 33;
        int y = 23;
        check(x < y);
//      ^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void Leq() {
        int x = 3;
        int y = 23;
        check(x <= y);
    }

    static void LeqKO() {
        int x = 33;
        int y = 23;
        check(x <= y);
//      ^^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void Geq() {
        int x = 3;
        int y = 23;
        check(y >= x);
    }

    static void GeqKO() {
        int x = 33;
        int y = 23;
        check(y >= x);
//      ^^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void Eq() {
        int x = 3;
        int y = 3;
        check(x == y);
    }

    static void EqKO() {
        int x = 4;
        int y = 3;
        check(x == y);
//      ^^^^^^^^^^^^^ Error: assertion does not hold
    }

    void InstanceOfTrivial() {
        check(this instanceof Operators);
    }

    void InstanceOfDummyClass() {
        DummyClass dc = new DummyClass();
        check(dc instanceof DummyClass);
    }

    void InstanceOfKO() {
        DummyInterface di = DummyClass.create();
        check(di instanceof DummyClass2);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold

    }

    @SuppressWarnings({"RedundantCast", "ConstantValue"})
    void castNumTrivial(int i) {
        check(i == (int) i);
        check(i == (long) i);
        check(i == (int) (long) i);
    }

    void castNumInvalid(int i) {
        check(i == (short) i);
//                 ^^^^^^^^^ Error: result of operation could not be proved to satisfy subset type constraint for 'int16'
    }

    void castRefTrivial() {
        //noinspection ConstantValue,RedundantCast
        check(this == (Operators) this);
    }

    void castDummyClass() {
        DummyClass dc1 = new DummyClass();
        DummyInterface dc2 = new DummyClass2();
        //noinspection ConstantValue,NewObjectEquality,RedundantCast
        check((DummyInterface) dc1 != dc2);
    }

    void castRefInvalid() {
        DummyClass dc1 = new DummyClass();
        DummyInterface dc2 = new DummyClass2();

        //noinspection RedundantCast,NewObjectEquality,DataFlowIssue
        var casted = (DummyClass) dc2;
//                   ^^^^^^^^^^^^^^^^ Error: value of expression (of type 'DummyInterface') is not known to be an instance of type 'DummyClass'
        check(dc1 == casted);
//      ^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
