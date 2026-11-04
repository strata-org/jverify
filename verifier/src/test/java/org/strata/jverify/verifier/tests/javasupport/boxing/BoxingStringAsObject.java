// A string used as an Object, then cast to a box class. A string literal is a
// Laurel string, not a composite, so Laurel's type check rejects it where it is
// stored as an Object, and the method is not verified.
package org.strata.jverify.verifier.tests.javasupport.boxing;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 4, methodsVerified = 0, methodsInvalid = 1, methodsSkipped = 1, errorCount = 1)
class BoxingStringAsObject {
    static void stringAsObject() {
        Object o = "hi";
//      ^^^^^^^^^^^^^^^^ Error: expected 'Object', got 'string'
        Integer i = (Integer) o;
    }
}
