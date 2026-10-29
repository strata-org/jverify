package org.strata.jverify.langref.jls11.s11_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.1 -- The Kinds of Exceptions: checked exceptions, unchecked
// runtime exceptions, and errors, distinguished by being caught and observed.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_1.KindsOfExceptions#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class KindsOfExceptions {
    static void run() {
        int kinds = 0;

        // Checked exception: subclass of Exception but not RuntimeException.
        try {
            throwChecked();
        } catch (MyCheckedException e) {
            kinds += 1;
        }

        // Unchecked runtime exception: subclass of RuntimeException.
        try {
            throw new IllegalStateException("unchecked");
        } catch (RuntimeException e) {
            kinds += 2;
        }

        // Error: subclass of Error, also unchecked.
        try {
            throw new MyError();
        } catch (Error e) {
            kinds += 4;
        }

        check(kinds == 7);
    }

    static void throwChecked() throws MyCheckedException {
        throw new MyCheckedException();
    }
}

class MyCheckedException extends Exception {
    MyCheckedException() {
        super();
    }
}

class MyError extends Error {
    MyError() {
        super();
    }
}
