package org.strata.jverify.verifier.tests.javasupport.library;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A class that the program declares and never allocates is the class of no
 * receiver: a record whose generated {@code equals} is not translated does not
 * refuse {@code Object.equals} on an {@code Object}, which runs.
 */
@JVerifyTest(interpret = true, entry = "org.strata.jverify.verifier.tests.javasupport.library.LibraryDispatchUnallocatedClass#run",
        expectedToFail = true, blockedBy = {Blocker.STRATA_OTHER},
        exitCode = 0, methodsInvalid = 0, methodsSkipped = 0, errorCount = 0)
class LibraryDispatchUnallocatedClass {
    record Unused(int x) {
    }

    static void run() {
        Object a = new Object();
        check(a.equals(a));
        check(!a.equals(new Object()));
    }
}
