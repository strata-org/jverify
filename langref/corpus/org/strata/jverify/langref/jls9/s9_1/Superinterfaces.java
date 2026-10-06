package org.strata.jverify.langref.jls9.s9_1;

import static org.strata.jverify.JVerify.*;

// JLS 9.1.3 -- Superinterfaces and Subinterfaces: an interface extending two
// superinterfaces; a class implements the subinterface and inherits all members.
class Superinterfaces {
    static void run() {
        Combined c = new Impl();
        int sum = c.a() + c.b() + c.c();
        check(sum == 6);
    }
}

interface First {
    int a();
}

interface Second {
    int b();
}

interface Combined extends First, Second {
    int c();
}

class Impl implements Combined {
    public int a() { return 1; }
    public int b() { return 2; }
    public int c() { return 3; }
}
