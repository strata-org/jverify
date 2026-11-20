package org.strata.jverify.langref.jls15.s15_8;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.8.4 -- Qualified this: TypeName.this refers to the enclosing instance from within an inner class
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_8.QualifiedThis_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_ASSERT_FAILED})
class QualifiedThis_2 {
    int outerValue = 10;

    class Inner {
        int outerValue = 99;

        int readOuter() {
            // QualifiedThis.this accesses the enclosing instance, distinct from Inner's own field
            return QualifiedThis_2.this.outerValue;
        }

        int readInner() {
            return this.outerValue;
        }
    }

    static void run() {

        QualifiedThis_2 outer = new QualifiedThis_2();
        QualifiedThis_2.Inner inner = outer.new Inner();

        check(inner.readInner() == 99);
    }
}
