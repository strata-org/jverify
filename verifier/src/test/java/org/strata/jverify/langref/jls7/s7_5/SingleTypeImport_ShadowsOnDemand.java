package org.strata.jverify.langref.jls7.s7_5;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

import java.util.Date;
import java.sql.*;

// JLS 7.5.1 / 7.5.2 -- a single-type-import declaration shadows an
// on-demand type-import declaration for the same simple name. Both
// `java.util.Date` (imported by simple name) and `java.sql.Date` (imported
// on demand) provide `Date`, but the single-type import wins.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls7.s7_5.SingleTypeImport_ShadowsOnDemand#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class SingleTypeImport_ShadowsOnDemand {
    static void run() {
        Date d = new Date(0L);
        check(d.getClass() == java.util.Date.class);
    }
}
