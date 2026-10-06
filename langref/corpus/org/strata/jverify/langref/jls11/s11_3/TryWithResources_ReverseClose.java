package org.strata.jverify.langref.jls11.s11_3;

import static org.strata.jverify.JVerify.*;

// JLS 14.20.3 -- multiple resources are closed in reverse of their
// declaration order.
class TryWithResources_ReverseClose {
    static StringBuilder log = new StringBuilder();

    static class R1 implements AutoCloseable {
        public void close() {
            log.append("1");
        }
    }

    static class R2 implements AutoCloseable {
        public void close() {
            log.append("2");
        }
    }

    static void run() {
        log.setLength(0);
        try (R1 r1 = new R1(); R2 r2 = new R2()) {
            // no-op body
        }
        check(log.toString().equals("21"));
    }
}
