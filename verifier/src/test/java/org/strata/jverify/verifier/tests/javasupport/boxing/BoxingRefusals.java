// The boxing constructs the box model refuses, each in its own method: reference
// comparison of two boxes (Java's box cache decides it), also when they come back
// from a generic method or through a generic type that names a wrapper class or is
// raw, unboxing a null box, unboxing or equals on a box read from a field or array
// (it may be null), boxing inside a contract, a wrapper constructor that does not
// take the primitive, and equals on an argument that is not a local.
package org.strata.jverify.verifier.tests.javasupport.boxing;

import java.util.List;

import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsInvalid = 0, errorCount = 0, methodsVerified = 9, methodsSkipped = 27)
class BoxingRefusals {
    static boolean sameBox(int x) {
        Integer a = x;
        Integer b = x;
        //noinspection NumberEquality
        return a == b;
//               ^ error: '==' on boxed values is not yet supported: their identity depends on the box cache
    }

    static boolean otherBox(int x) {
        Integer a = x;
        Integer b = x;
        //noinspection NumberEquality
        return a != b;
//               ^ error: '!=' on boxed values is not yet supported: their identity depends on the box cache
    }

    static <T> T id(T x) {
        return x;
    }

    static boolean sameGenericResult() {
        return id(1) == id(1);
//                   ^ error: '==' on boxed values is not yet supported: their identity depends on the box cache
    }

    static int unboxNull() {
//                         ^ error: Unsupported expression: AnonymousDynamicConstantDesc
        Integer x = null;
        return x;
    }

    @Transparent
    static boolean positive(Integer b) {
        return b > 0;
    }

    static void boxInContract(int x) {
//                                   ^ error: boxing inside a contract is not yet supported
        precondition(positive(x));
    }

    @SuppressWarnings("removal")
    static int stringConstructor() {
//                                 ^ error: constructor Integer(String) is not yet supported
        return new Integer("5");
    }

    static Object anObject() {
        return Integer.valueOf(1);
    }

    static boolean equalsCallResult(Integer a) {
//                                             ^ error: 'Integer.equals' on an argument that is not a local variable is not yet supported
        return a.equals(anObject());
    }

    static class Holder {
        Integer f;
    }

    Integer field;

    static int unassignedField() {
        int x = new Holder().f;
//                           ^ error: unboxing a box read from a field or array is not yet supported: it may be null
        return x;
    }

    void fieldStore(int x) {
        field = x;
        check(field == x);
//            ^ error: unboxing a box read from a field or array is not yet supported: it may be null
    }

    static int fieldThroughLocal(Holder h) {
        Integer b = h.f;
        return b;
//             ^ error: unboxing a box read from a field or array is not yet supported: it may be null
    }

    static int fieldThroughCast(Object o) {
        Holder h = (Holder) o;
        Object v = h.f;
        return (Integer) v;
//                       ^ error: unboxing a box read from a field or array is not yet supported: it may be null
    }

    static int fieldThroughConditional(Holder h, boolean flag, int x) {
        Integer b = flag ? h.f : Integer.valueOf(x);
        return b;
//             ^ error: unboxing a box read from a field or array is not yet supported: it may be null
    }

    static int conditionalUnboxed(Holder h, boolean flag, int x) {
        int r = flag ? h.f : Integer.valueOf(x);
//                                             ^ error: unboxing a box read from a field or array is not yet supported: it may be null
        return r;
    }

    static int assignedInThenBranch(Holder h, boolean c, int x) {
        Integer b;
        if (c) {
            b = h.f;
        } else {
            b = Integer.valueOf(x);
        }
        return b;
//             ^ error: unboxing a box read from a field or array is not yet supported: it may be null
    }

    static int assignedInSwitchCase(Holder h, int k, int x) {
        Integer b;
        switch (k) {
            case 0:
                b = h.f;
                break;
            default:
                b = Integer.valueOf(x);
        }
        return b;
//             ^ error: unboxing a box read from a field or array is not yet supported: it may be null
    }

    static int chainedAssignment(Holder h) {
        Integer c;
        Integer b = c = h.f;
        return b;
//             ^ error: unboxing a box read from a field or array is not yet supported: it may be null
    }

    static int arrayElement(Integer[] a) {
//                                       ^ error: arrays of reference types are not yet supported
        return a[0];
    }

    static boolean equalsField(Holder h, int x) {
        return h.f.equals(x);
//                 ^ error: 'Integer.equals' on a box read from a field or array is not yet supported: it may be null
    }

    static boolean equalsFieldArgument(Holder h, int x) {
        Integer a = x;
        return a.equals(h.f);
//               ^ error: 'Integer.equals' on a box read from a field or array is not yet supported: it may be null
    }

    static class Box<T> {
        final T v;

        Box(T v) {
            this.v = v;
        }

        T get() {
            return v;
        }
    }

    static boolean sameBoxContent(Box<Integer> p, Box<Integer> q) {
        return p.get() == q.get();
//                     ^ error: '==' on boxed values is not yet supported: their identity depends on the box cache
    }

    static boolean sameNewBoxContent() {
        Box<Integer> p = new Box<>(1000);
        Box<Integer> q = new Box<>(1000);
        return p.get() == q.get();
//                     ^ error: '==' on boxed values is not yet supported: their identity depends on the box cache
    }

    static <T> T first(List<T> xs) {
//                                 ^ error: call to 'get' on a receiver whose static type is not a class is not yet supported
        return xs.get(0);
    }

    static boolean sameFirst(List<Integer> xs, List<Integer> ys) {
        return first(xs) == first(ys);
//                       ^ error: '==' on boxed values is not yet supported: their identity depends on the box cache
    }

    static class Pair<K, V> {
        final K k;
        final V v;

        Pair(K k, V v) {
            this.k = k;
            this.v = v;
        }

        K first() {
            return k;
        }

        V second() {
            return v;
        }
    }

    static boolean sameFirstOfPair(Pair<Integer, Holder> p, Pair<Integer, Holder> q) {
        return p.first() == q.first();
//                       ^ error: '==' on boxed values is not yet supported: their identity depends on the box cache
    }

    static boolean sameSecondOfPair(Pair<Integer, Holder> p, Pair<Integer, Holder> q) {
        return p.second() == q.second();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static boolean sameRawBoxContent(Box p, Box q) {
        return p.get() == q.get();
//                     ^ error: '==' on boxed values is not yet supported: their identity depends on the box cache
    }
}
