package org.strata.jverify.langref.jls8.s8_10;

import static org.strata.jverify.JVerify.*;

// JLS 8.10.3 -- Record Members: for each record component, an accessor method with the
// same name and return type is implicitly declared, unless the programmer explicitly
// declares one. Here the accessor is explicitly declared (overriding the implicit one).
class RecordMembers_2 {
    record Box(int amount) {
        // explicitly declared accessor matching the implicit one's name and return type
        public int amount() {
            return amount;
        }

        int doubled() {
            return amount() * 2;
        }
    }

    static void run() {

        Box b = new Box(21);

        check(b.doubled() == 42);
    }
}
