package org.strata.jverify.langref.jls9.s9_7;

import static org.strata.jverify.JVerify.*;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// JLS 9.7.2 -- Marker Annotations: an annotation with no element-value pairs,
// a shorthand for a normal annotation with empty parentheses; its presence is
// observed via reflection at runtime.
class MarkerAnnotation {
    @Retention(RetentionPolicy.RUNTIME)
    @interface Marked {
    }

    @Marked
    static class Target {
    }

    static void run() {
        boolean present = Target.class.isAnnotationPresent(Marked.class);
        check(present);
    }
}
