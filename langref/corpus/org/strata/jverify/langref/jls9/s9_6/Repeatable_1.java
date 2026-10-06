package org.strata.jverify.langref.jls9.s9_6;

import static org.strata.jverify.JVerify.*;

// JLS 9.6.3 -- @Repeatable: an annotation type marked @Repeatable(Container.class)
// may be applied multiple times to the same target; getAnnotationsByType returns
// all occurrences.
class Repeatable_1 {
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @interface Tags {
        Tag[] value();
    }

    @java.lang.annotation.Repeatable(Tags.class)
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @interface Tag {
        String value();
    }

    @Tag("a")
    @Tag("b")
    static class C {
    }

    static void run() {
        check(C.class.getAnnotationsByType(Tag.class).length == 2);
    }
}
