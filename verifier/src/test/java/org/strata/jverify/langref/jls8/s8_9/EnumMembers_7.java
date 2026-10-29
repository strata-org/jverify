package org.strata.jverify.langref.jls8.s8_9;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.9.3 -- Enum Members: implicit values(), valueOf(String), ordinal() and name().
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_9.EnumMembers_7#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class EnumMembers_7 {
    enum Color { RED, GREEN, BLUE }

    static void run() {

        Color[] all = Color.values();

        check(Color.GREEN.name().equals("GREEN"));
    }
}
