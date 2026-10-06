package org.strata.jverify.langref.jls9.s9_6;

import static org.strata.jverify.JVerify.*;

// JLS 9.6.1 -- an annotation-interface element may have an enum type; the
// selected constant is preserved and readable at runtime.
class AnnotationElement_EnumType {
    enum Level {
        LOW, HIGH
    }

    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @interface Priority {
        Level value();
    }

    @Priority(Level.HIGH)
    static class C {
    }

    static void run() {
        check(C.class.getAnnotation(Priority.class).value() == Level.HIGH);
    }
}
