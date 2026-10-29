package org.strata.jverify.langref.jls11.s11_3;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.20.2 -- a return in finally overrides the return value of the try
// block: m() returns 2, not 1.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_3.Finally_OverridesReturn#run")
class Finally_OverridesReturn {
    @SuppressWarnings("finally")
    static int m() {
        try {
            return 1;
        } finally {
            return 2;
        }
    }

    static void run() {
        check(m() == 2);
    }
}
