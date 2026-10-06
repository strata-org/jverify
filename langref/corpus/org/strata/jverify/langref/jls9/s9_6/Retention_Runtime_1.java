package org.strata.jverify.langref.jls9.s9_6;

import static org.strata.jverify.JVerify.*;

// JLS 9.6.4.2 -- @Retention(RUNTIME): the annotation is retained in the class
// file AND made available at runtime via reflection.
class Retention_Runtime_1 {
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @interface Tag {
    }

    @Tag
    static class C {
    }

    static void run() {
        check(C.class.isAnnotationPresent(Tag.class));
    }
}
