// Library calls that bind to a contract Strata cannot accept refuse only the
// method that makes them; the plain method still verifies.
package org.strata.jverify.verifier.tests.jcl;

import org.strata.jverify.testengine.JVerifyTest;

import java.math.BigInteger;
import java.util.Optional;

@JVerifyTest(useBuiltinContracts = true, continueOnErrors = true, methodsVerified = 2, methodsSkipped = 7)
class UnsupportedContractCalls {
    static int literalReceiver(String s) {
//                                       ^ error: call to 'length' on a receiver whose static type is not a class is not yet supported
        return ("a" + s).length();
    }

    static <T> boolean typeVariableReceiver(T a, T b) {
//                                                    ^ error: call to 'equals' on a receiver whose static type is not a class is not yet supported
        return a.equals(b);
    }

    static int callResultReceiver(String s) {
//                                          ^ error: call to 'length' on a receiver whose static type is not a class is not yet supported
        return s.substring(1).length();
    }

    static boolean untranslatableMember(String a, String b) {
//                                                          ^ error: call to 'equals', which could not be translated: instanceof on opaque reference types is not yet supported
        return a.equals(b);
    }

    boolean memberWithAChainedCall(String s, String t) {
//                                                     ^ warning: instance method 'memberWithAChainedCall' was not verified: call to 'startsWith', which could not be translated: call to 'startsWith', which could not be translated: call to 'take' on the result of another call is not yet supported
        return s.startsWith(t);
    }

    Object recursiveMember(BigInteger b) {
//                                       ^ warning: instance method 'recursiveMember' was not verified: call to 'pow', which could not be translated: call to 'pow', which could not be translated: a recursive contract function is not yet supported: its 'decreases' clause is not emitted
        return b.pow(2);
    }

    Object unfixedTypeParameter() {
//                                ^ warning: instance method 'unfixedTypeParameter' was not verified: call to 'empty', which could not be translated: type parameter 'T' that no argument fixes is not yet supported
        return Optional.empty();
    }

    static int ok(int x) {
        return x;
    }
}
