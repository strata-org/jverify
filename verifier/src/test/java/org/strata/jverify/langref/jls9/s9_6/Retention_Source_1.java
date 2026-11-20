package org.strata.jverify.langref.jls9.s9_6;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 9.6.4.2 -- @Retention(SOURCE): the annotation is discarded by the
// compiler, so reflection cannot see it at runtime.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls9.s9_6.Retention_Source_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Retention_Source_1 {
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE)
    @interface Tag {
    }

    @Tag
    static class C {
    }

    static void run() {
        check(!C.class.isAnnotationPresent(Tag.class));
    }
}
