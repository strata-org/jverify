package org.strata.jverify.langref.jls8.s8_9;

import static org.strata.jverify.JVerify.*;

// JLS 8.9.3 -- Enum Members: implicit values(), valueOf(String), ordinal() and name().
class EnumMembers_2 {
    enum Color { RED, GREEN, BLUE }

    static void run() {

        check(Color.GREEN.ordinal() == 1);
    }
}
