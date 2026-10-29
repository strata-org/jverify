package org.strata.jverify.langref.jls8.s8_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.10 -- a record class may implement interfaces; the record body supplies
// the interface method(s).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_10.Record_ImplementsInterface#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Record_ImplementsInterface {
    interface Named {
        String label();
    }

    record Person(String name) implements Named {
        public String label() {
            return name;
        }
    }

    static void run() {
        check(new Person("Alice").label().equals("Alice"));
    }
}
