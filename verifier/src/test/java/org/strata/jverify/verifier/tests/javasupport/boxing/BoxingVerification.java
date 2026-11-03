// Boxing and unboxing of the wrapper classes, without library contracts: a box
// is a composite holding its primitive, so it can be stored, passed, returned,
// kept in a field, compared with equals, used as an Object or one of its
// wrapper's supertypes (Number, Comparable, Serializable) and cast back.
package org.strata.jverify.verifier.tests.javasupport.boxing;

import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 4, methodsVerified = 22, methodsInvalid = 3, errorCount = 3)
class BoxingVerification {
    Integer field;

    static void boxStoreUnbox(int x) {
        Integer b = x;
        int y = b;
        check(y == x);
    }

    static void unboxWrongValue(int x) {
        Integer b = x;
        check(b == x + 1);
//      ^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
    }

    static Integer box(int x) {
        postcondition((Integer r) -> r == x);
        return x;
    }

    static int unbox(Integer b) {
        postcondition((int r) -> r == b);
        return b;
    }

    static void argumentAndReturn(int x) {
        check(unbox(box(x)) == x);
    }

    void fieldStore(int x) {
        field = x;
        check(field == x);
    }

    static void equalsSameValue(int x) {
        Integer a = x;
        Integer b = x;
        check(a.equals(b));
    }

    static void equalsTracksValue(int x, int y) {
        Integer a = x;
        Integer b = y;
        check(a.equals(b) == (x == y));
    }

    static void equalsBoxedArgument(int x) {
        Integer a = x;
        check(a.equals(x));
    }

    static void equalsObject(int x) {
        Integer a = x;
        Object o = Integer.valueOf(x);
        check(a.equals(o));
    }

    static void equalsOtherBoxClass(int x) {
        Integer a = x;
        Object o = Long.valueOf(x);
        check(!a.equals(o));
    }

    static void takesNumber(Number n) {
    }

    static void takesComparable(Comparable<Integer> c) {
    }

    static void takesSerializable(java.io.Serializable s) {
    }

    static void supertypes(int x, byte y) {
        takesNumber(x);
        takesComparable(x);
        takesSerializable(y);
        Number n = x;
        check(n instanceof Integer && (Integer) n == x);
    }

    @Transparent
    static Integer transparentBox(int x) {
        return x;
    }

    static void castBack(int x) {
        Object o = x;
        int y = (Integer) o;
        check(y == x);
    }

    static void badCast(long x) {
        Object o = x;
        Integer i = (Integer) o;
//      ^^^^^^^^^^^^^^^^^^^^^^^^ Error: uncaught exception 'java.lang.ClassCastException' is not thrown could not be proved
    }

    static void instanceOf(int x) {
        Object o = x;
        check(o instanceof Integer && !(o instanceof Long));
    }

    static void otherWrappers(long l, boolean z, char c, short s, byte by) {
        Long bl = l;
        Boolean bz = z;
        Character bc = c;
        Short bs = s;
        Byte bb = by;
        check(bl == l && bz == z && bc == c && bs == s && bb == by);
    }

    static void constructor(int x) {
        @SuppressWarnings("removal")
        Integer b = new Integer(x);
        check(b == x);
    }

    static void loop(int n) {
        precondition(0 <= n && n < 100);
        Integer acc = 0;
        for (int i = 0; i < n; i++) {
            invariant(i <= n && acc == i);
            acc = acc + 1;
        }
        check(acc == n);
    }

    static void boxedInSpec(Integer b, int x) {
        precondition(b.equals(x));
        check(b == x);
    }

    static void loopWrongBound(int n) {
        precondition(0 <= n && n < 100);
        Integer acc = 0;
        for (int i = 0; i < n; i++) {
            invariant(i <= n && acc == i);
            acc = acc + 1;
        }
        check(acc == n + 1);
//      ^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
    }
}
