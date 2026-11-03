// The boxing constructs the box model refuses, each in its own method: reference
// comparison of two boxes (Java's box cache decides it), also when they come back
// from a generic method, unboxing a null box,
// boxing inside a contract, a wrapper constructor that does not take the
// primitive, and equals on an argument that is not a local.
package org.strata.jverify.verifier.tests.javasupport.boxing;

import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsInvalid = 0, errorCount = 0, methodsVerified = 4, methodsSkipped = 7)
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
}
