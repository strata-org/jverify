package org.strata.jverify.langref.jls15.s15_12;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.12.1 -- Determine Type to Search: the type in which to search for a method
// is determined by the form of the invocation: a simple name uses the enclosing
// class, ExpressionName.method uses the variable's type, and super.method uses the
// superclass.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_12.DetermineTypeToSearch_3#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class DetermineTypeToSearch_3 {
    static int self() { return 1; }

    static void run() {

        // super.method via the child -> search the superclass.
        int viaSuper = new DtsChild_DetermineTypeToSearch_3().both();
        check(viaSuper == 110);
    }
}

class DtsBase_DetermineTypeToSearch_3 {
    int label() { return 10; }
}

class DtsChild_DetermineTypeToSearch_3 extends DtsBase_DetermineTypeToSearch_3 {
    @Override
    int label() { return 20; }

    int both() {
        // super.label() searches the superclass type (DtsBase): 10.
        // this.label() searches the dynamic type (DtsChild): 20 -> wait, super part is static.
        return super.label() + this.label() + 80;
    }
}
