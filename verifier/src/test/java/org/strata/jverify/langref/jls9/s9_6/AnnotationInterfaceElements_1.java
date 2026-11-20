package org.strata.jverify.langref.jls9.s9_6;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 9.6.1 -- Annotation Interface Elements: declare an annotation interface with
// elements (no defaults), apply it, and read element values via reflection.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls9.s9_6.AnnotationInterfaceElements_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class AnnotationInterfaceElements_1 {
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @interface Config {
        int count();
        String name();
    }

    @Config(count = 7, name = "abc")
    static class Target {
    }

    static void run() {

        Config c = Target.class.getAnnotation(Config.class);
        check(c.count() == 7);
    }
}
