package org.strata.jverify.langref.jls9.s9_6;

import static org.strata.jverify.JVerify.*;

// JLS 9.6.1 -- an annotation-interface element type may be an array of a valid
// element type; the value at each index is preserved.
class AnnotationElement_ArrayType {
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @interface Config {
        int[] values();
    }

    @Config(values = {1, 2, 3})
    static class C {
    }

    static void run() {
        check(C.class.getAnnotation(Config.class).values()[1] == 2);
    }
}
