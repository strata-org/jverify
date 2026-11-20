package org.strata.jverify.langref.jls14.s14_18;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.18 -- The throw Statement: a throw transfers control by raising an
// exception object; a matching try/catch in an enclosing scope catches it and
// execution resumes after the catch. Also exercises rethrow and the abrupt
// completion of the throwing block.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_18.ThrowStatement_3#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ThrowStatement_3 {
    static int raise(int v) {
        if (v < 0) {
            throw new IllegalArgumentException("negative");
        }
        return v;
    }

    static int rethrow(int v) {
        try {
            return raise(v);
        } catch (IllegalArgumentException e) {
            // rethrow a different exception type
            throw new IllegalStateException(e);
        }
    }

    static void run() {

        // a throw inside a catch propagates as the new type
        int wrapped = 0;
        try {
            rethrow(-5);
        } catch (IllegalStateException e) {
            wrapped = 2;
        }
        check(wrapped == 2);
    }
}
