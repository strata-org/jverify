package org.strata.jverify.langref.jls9.s9_6;

import static org.strata.jverify.JVerify.*;

// JLS 9.6.4.2 -- @Retention(SOURCE): the annotation is discarded by the
// compiler, so reflection cannot see it at runtime.
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
