package org.strata.jverify.langref.jls8.s8_1;

import static org.strata.jverify.JVerify.*;

// JLS 8.1.5 -- Superinterfaces: an "implements" clause names direct
// superinterfaces; the class supplies implementations for their methods.
class Superinterfaces {
    static void run() {
        Robot r = new Robot();
        Named n = r;
        Aged a = r;
        check(n.nameLength() + a.age() == 30);
    }
}

interface Named {
    int nameLength();
}

interface Aged {
    int age();
}

class Robot implements Named, Aged {
    @Override
    public int nameLength() {
        return 10;
    }

    @Override
    public int age() {
        return 20;
    }
}
