package org.strata.jverify.langref.jls9.s9_6;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 9.6.2 -- Defaults for Annotation Interface Elements: an element declared with a
// default value may be omitted at the use site, taking the declared default.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls9.s9_6.DefaultsForElements_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class DefaultsForElements_1 {
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @interface Sized {
        int width() default 10;
        int height() default 20;
    }

    @Sized(width = 5)
    static class Box {
    }

    static void run() {

        Sized s = Box.class.getAnnotation(Sized.class);
        check(s.width() == 5);
    }
}
