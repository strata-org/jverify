package org.strata.jverify.langref.jls9.s9_6;

import static org.strata.jverify.JVerify.*;

// JLS 9.6.1 -- an annotation-interface element may have type Class or a
// parameterisation of Class; the class literal is preserved at runtime.
class AnnotationElement_ClassType {
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @interface Kind {
        Class<?> value();
    }

    @Kind(String.class)
    static class C {
    }

    static void run() {
        check(C.class.getAnnotation(Kind.class).value() == String.class);
    }
}
