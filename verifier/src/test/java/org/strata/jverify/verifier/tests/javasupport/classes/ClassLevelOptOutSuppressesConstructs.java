package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * A class-level {@code @Verify(false)} suppresses the unsupported-construct errors for
 * everything it covers, including the constructs that are NOT inside a method body.
 *
 * <p>{@code VerifyAnnotationCompiler} strips method bodies only, so an initializer block,
 * a record's hand-written member, and a bitwise operator in a FIELD initializer all
 * survive into this pass and used to fail the build even though the whole class was
 * opted out -- contradicting the documented "honored on the method and on any enclosing
 * class".
 */
@JVerifyTest(exitCode = 0)
@Verify(false)
class ClassLevelOptOutSuppressesConstructs {

    int fieldWithBitwise = 5 & 3;

    int viaInitializerBlock;

    {
        viaInitializerBlock = 7;
    }

    int bitwiseInBody(int x, int y) {
        return x | y;
    }

    record HandWritten(int i) {
        public int i() {
            return i;
        }
    }
}
