package org.strata.jverify.langref.jls8.s8_8;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.8.9 -- Default Constructor: a class with no declared constructors gets an implicit default constructor with no parameters.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_8.DefaultConstructor#run")
class DefaultConstructor {
    int field = 42;

    static void run() {
        // DefaultClass declares no constructor; the implicit default is invoked here.
        DefaultClass d = new DefaultClass();
        check(d.field == 42);
    }
}

class DefaultClass {
    int field = 42;
}
