package org.strata.jverify.langref.jls15.s15_12;

import static org.strata.jverify.JVerify.*;

// JLS 15.12.1 -- Determine Type to Search: the type in which to search for a method
// is determined by the form of the invocation: a simple name uses the enclosing
// class, ExpressionName.method uses the variable's type, and super.method uses the
// superclass.
class DetermineTypeToSearch_2 {
    static int self() { return 1; }

    static void run() {

        // ExpressionName.method -> search the static type of the receiver expression.
        DtsBase_DetermineTypeToSearch_2 b = new DtsChild_DetermineTypeToSearch_2();
        int viaInstance = b.label();
        check(viaInstance == 20);
    }
}

class DtsBase_DetermineTypeToSearch_2 {
    int label() { return 10; }
}

class DtsChild_DetermineTypeToSearch_2 extends DtsBase_DetermineTypeToSearch_2 {
    @Override
    int label() { return 20; }

    int both() {
        // super.label() searches the superclass type (DtsBase): 10.
        // this.label() searches the dynamic type (DtsChild): 20 -> wait, super part is static.
        return super.label() + this.label() + 80;
    }
}
