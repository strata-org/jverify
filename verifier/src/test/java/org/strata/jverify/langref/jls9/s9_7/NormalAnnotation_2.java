package org.strata.jverify.langref.jls9.s9_7;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// JLS 9.7.1 -- Normal Annotations: an annotation with a parenthesized list of
// comma-separated element-value pairs, read back via reflection at runtime.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls9.s9_7.NormalAnnotation_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class NormalAnnotation_2 {
    @Retention(RetentionPolicy.RUNTIME)
    @interface Config {
        int id();
        String label();
    }

    @Config(id = 7, label = "abc")
    static class Target {
    }

    static void run() {

        Config c = Target.class.getAnnotation(Config.class);

        check(c.label().equals("abc"));
    }
}
