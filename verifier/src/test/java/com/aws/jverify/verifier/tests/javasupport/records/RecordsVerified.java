package com.aws.jverify.verifier.tests.javasupport.records;

import com.aws.jverify.*;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 4, methodsVerified = 22, errorCount = 4)
class RecordsVerified {
    static void unitRecord() {
        var _ = new UnitRecord();
    }
    
    static void equalsOnRecords() {
        var first = new IntRecord(1);
        var second = new IntRecord(1);
        var third = new IntRecord(2);
        check(first.equals(second));
        check(!first.equals(third));
    }
    
    static void primitiveRecords() {
        var neg = new IntRecord(-1);
        var pos = new IntRecord(2);
        var big = new IntRecord(999);

        check(neg.value() == -1);
        check(pos.value() == 2);

        check(big.value() == 777);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void referenceRecords() {
        var foo1 = new Foobar(1);
        var foo2 = new Foobar(2);

        var rec1 = new FoobarRecord(foo1);
        var rec2 = new FoobarRecord(foo2);

        check(rec1.foobar().id == 1);

        check(rec2.foobar().id == 3);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void genericRecords() {
        var intRecordPair = new Pair<IntRecord, IntRecord>(new IntRecord(1), new IntRecord(2));
        check(intRecordPair.a().value() == 1);
        check(intRecordPair.b().value() == 2);

        var foobar = new Foobar(3);
        var foobarRecord = new FoobarRecord(foobar);
        check(foobarRecord.foobar() == null);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold

        var foobarFoobarRecord = new Pair<Foobar, FoobarRecord>(foobar, foobarRecord);
        check(foobarFoobarRecord.b().foobar() == null);
    }
    
    static void unusedGenericType() {
        // Do not store the record itself in a variable, because we want to test not capturing the type
        var someInt3 = new UnusedTypeParameter<IntRecord, IntRecord>(3).x();
    }

    static void recursiveRecords() {
        var nodeC = new BasicConsList(3, null);
        var contC = new Wrapper<>(nodeC);
        var nodeB = new BasicConsList(2, contC);
        var contB = new Wrapper<>(nodeB);
        var nodeA = new BasicConsList(1, contB);

        check(nodeA.head() == 1);
        check(nodeB.head() == 2);
        check(nodeC.head() == 3);

        check(nodeA.tail() == null);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
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
    
    static void assignRecordToObject() {
        Object o = new IntRecord(3);
    }
}

/** No components */
record UnitRecord() {}

/** Primitive-type component */
record IntRecord(int value) {
    public void useValueInsideRecord() {
        var b = value();
    }
}

/** Reference-type component */
record FoobarRecord(@Nullable Foobar foobar) {}

/** Generic-type record and components */
record Pair<A, B>(A a, B b) {}

/** Generic-type record and components */
record UnusedTypeParameter<A, B>(int x) {}

/** Recursion */
record BasicConsList(int head, @Nullable Wrapper<BasicConsList> tail) {}

/** Members */
@SuppressWarnings("ManualMinMaxCalculation")
record Vec3(int x, int y, int z) {
    @Pure
    public int max() {
        postcondition((int m) -> m >= x && m >= y && m >= z);
        postcondition((int m) -> m == x || m == y || m == z);
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
 * In Strata, records are translated to value types,
 * which don't automatically yield both nullable and non-nullable types (as classes do).
 * This wrapper class works around JVerify's current lack of automatic handling
 * for values that are of reference types in Java but of value types in Strata.
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
    
    @Contract
    class CoefficientContract implements ICoefficient {

        @Pure
        @Override
        public @Unbounded int times(int i) {
            throw new ContractException();
        }
    }
}

@Verify(false)
record HasConstructor(int x) {
    HasConstructor() {
        this(3);
        postcondition(this.x() == 3);
    }
}
