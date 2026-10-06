package org.strata.jverify.langref.jls9.s9_6;

import static org.strata.jverify.JVerify.*;

// JLS 9.6.4.1 -- @Target(METHOD): the annotation is applicable only to method
// declarations, and reflection sees it on that method.
class Target_MethodOnly {
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
    @interface M {
    }

    static class C {
        @M
        void m() {
        }
    }

    static void run() {
        boolean ok = false;
        try {
            ok = C.class.getDeclaredMethod("m").isAnnotationPresent(M.class);
        } catch (Exception e) {
        }
        check(ok);
    }
}
