package org.strata.jverify.langref.jls9.s9_6;

import static org.strata.jverify.JVerify.*;

// JLS 9.6.4.3 -- @Inherited: a class annotation type marked @Inherited is
// automatically considered present on subclasses that do not have their own.
class Inherited_1 {
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Inherited
    @interface Tag {
    }

    @Tag
    static class Base {
    }

    static class Sub extends Base {
    }

    static void run() {
        check(Sub.class.isAnnotationPresent(Tag.class));
    }
}
