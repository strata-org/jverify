package org.strata.jverify.langref.jls15.s15_8;

import static org.strata.jverify.JVerify.*;

// JLS 15.8.4 -- Qualified this: TypeName.this refers to the enclosing instance from within an inner class
class QualifiedThis_1 {
    int outerValue = 10;

    class Inner {
        int outerValue = 99;

        int readOuter() {
            // QualifiedThis.this accesses the enclosing instance, distinct from Inner's own field
            return QualifiedThis_1.this.outerValue;
        }

        int readInner() {
            return this.outerValue;
        }
    }

    static void run() {

        QualifiedThis_1 outer = new QualifiedThis_1();
        QualifiedThis_1.Inner inner = outer.new Inner();
        check(inner.readOuter() == 10);
    }
}
