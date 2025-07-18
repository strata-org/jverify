package com.aws.jverify.verifier.tests;

import com.aws.jverify.*;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 4, dafnyVerified = 10, dafnyErrors = 4)
class RecordsVerified {
    static void unitRecord() {
        var _ = new UnitRecord();
    }

    static void primitiveRecords() {
        var neg = new IntRecord(-1);
        var pos = new IntRecord(2);
        var big = new IntRecord(999);

        check(neg.value() == -1);
        check(pos.value() == 2);

        check(big.value() == 777);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion might not hold
    }

    static void referenceRecords() {
        var foo1 = new Foobar(1);
        var foo2 = new Foobar(2);

        var rec1 = new FoobarRecord(foo1);
        var rec2 = new FoobarRecord(foo2);

        check(rec1.foobar().id == 1);

        check(rec2.foobar().id == 3);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion might not hold
    }

    static void genericRecords() {
        var intRecordPair = new Pair<IntRecord, IntRecord>(new IntRecord(1), new IntRecord(2));
        check(intRecordPair.a().value() == 1);
        check(intRecordPair.b().value() == 2);
//        var intBool = new Pair<Integer, Boolean>(1, true);
//        var longString = new Pair<Long, String>(2L, "foo");
//        check(intBool.a() * 2 == longString.a());
//        check(intBool.b());
//        check(longString.b().equals("foo"));

        var foobar = new Foobar(3);
        var foobarRecord = new FoobarRecord(foobar);
        check(foobarRecord.foobar() == null);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion might not hold

        var foobarFoobarRecord = new Pair<Foobar, FoobarRecord>(foobar, foobarRecord);
        check(foobarFoobarRecord.b().foobar() == null);

        // Do not store the record in a variable, because we want to test not capturing the type
//        var someInt = new Pair<Integer, Boolean>(1, true).a();
        var someInt3 = new UnusedTypeParameter<IntRecord, IntRecord>(3).x();
    }

    static void recursiveRecords() {
        var nodeC = new BasicConsList("C", null);
        var contC = new Wrapper<>(nodeC);
        var nodeB = new BasicConsList("B", contC);
        var contB = new Wrapper<>(nodeB);
        var nodeA = new BasicConsList("A", contB);

        check(nodeA.head().equals("A"));
        check(nodeB.head().equals("B"));
        check(nodeC.head().equals("C"));

        check(nodeA.tail() == null);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion might not hold
    }

    static void memberFunctions() {
        var vec = new Vec3(5, 0, 2);
        check(vec.max() == 5);

        var maxed = vec.allMax();
        check(maxed.x() == 5);
        check(maxed.y() == 5);
        check(maxed.z() == 5);
    }

    static void interfaceImplementingRecord() {
        var factor = new Factor(2);
        check(factor.times(8) == 16);

        // we can also create a new record instance within an expression
        check(new Factor(3).times(7) == 21);
    }
    
    static void hasConstructorRecord() {
        var hasConstructor = new HasConstructor();
        check(hasConstructor.x() == 3);
    }
    
    static void assignRecordToValueObject() {
        @Immutable Object o = new IntRecord(3);
    }
}

/** No components */
record UnitRecord() {}

/** Primitive-type component */
record IntRecord(int value) {}

/** Reference-type component */
record FoobarRecord(@Nullable Foobar foobar) {}

/** Generic-type record and components */
record Pair<A, B>(A a, B b) {}

/** Generic-type record and components */
record UnusedTypeParameter<A, B>(int x) {}

/** Recursion */
record BasicConsList(String head, @Nullable Wrapper<BasicConsList> tail) {}

/** Members */
@SuppressWarnings("ManualMinMaxCalculation")
record Vec3(int x, int y, int z) {
    @Pure
    public int max() {
        postcondition((Integer m) -> m >= x && m >= y && m >= z);
        postcondition((Integer m) -> m == x || m == y || m == z);
        return (x >= y && x >= z) ? x
                : (y >= z ? y : z);
    }

    @Pure
    public Vec3 allMax() {
        return new Vec3(this.max(), this.max(), this.max());
    }
}

record Factor(int value) implements ICoefficient {
    @Pure
    @Override
    public @Unbounded int times(int i) {
        return value * i;
    }
}

/**
 * Records are translated to Dafny datatypes,
 * which don't automatically yield both nullable and non-nullable types (as Dafny classes do).
 * This wrapper class works around JVerify's current lack of automatic handling
 * for values that are of reference types in Java but of value types in Dafny.
 * (See <a href="https://github.com/aws/jverify/issues/179">#179</a>.)
 */
class Wrapper<T> {
    T val;

    Wrapper(T val) {
        this.val = val;
    }
}

/** Arbitrary class to use within records */
class Foobar {
    int id;

    Foobar(int id) {
        postcondition((Foobar instance) -> instance.id == id);
        this.id = id;
    }
}

interface ICoefficient {
    @Unbounded int times(int i);
}

@Verify(false)
record HasConstructor(int x) {
    HasConstructor() {
        this(3);
        postcondition(this.x() == 3);
    }
}