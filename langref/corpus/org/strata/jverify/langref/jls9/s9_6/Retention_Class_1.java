package org.strata.jverify.langref.jls9.s9_6;

import static org.strata.jverify.JVerify.*;

// JLS 9.6.4.2 -- @Retention(CLASS): the annotation is stored in the class file
// but is NOT retained by the VM for runtime reflection.
class Retention_Class_1 {
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
    @interface Tag {
    }

    @Tag
    static class C {
    }

    static void run() {
        check(!C.class.isAnnotationPresent(Tag.class));
    }
}
