package org.strata.jverify.langref.jls8.s8_10;

import static org.strata.jverify.JVerify.*;

// JLS 8.10.1 -- Record Components: the header of a record declaration lists the record
// components, each of which gives rise to a private final field and a corresponding
// accessor, both implicitly declared.
class RecordComponents_2 {
    record Point(int x, int y) { }

    static void run() {

        Point p = new Point(3, 4);

        check(p.y() == 4);
    }
}
