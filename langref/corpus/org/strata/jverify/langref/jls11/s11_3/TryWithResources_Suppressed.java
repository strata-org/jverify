package org.strata.jverify.langref.jls11.s11_3;

import static org.strata.jverify.JVerify.*;

// JLS 14.20.3 -- if a try-with-resources body throws and close() also
// throws, the primary exception carries the close exception as suppressed.
class TryWithResources_Suppressed {
    static class CloseEx extends RuntimeException {
        CloseEx(String m) { super(m); }
    }

    static class BodyEx extends RuntimeException {
        BodyEx(String m) { super(m); }
    }

    static class R implements AutoCloseable {
        public void close() {
            throw new CloseEx("close");
        }
    }

    static void run() {
        boolean ok = false;
        try {
            try (R r = new R()) {
                throw new BodyEx("body");
            }
        } catch (BodyEx e) {
            Throwable[] suppressed = e.getSuppressed();
            ok = suppressed.length == 1 && suppressed[0] instanceof CloseEx;
        }
        check(ok);
    }
}
