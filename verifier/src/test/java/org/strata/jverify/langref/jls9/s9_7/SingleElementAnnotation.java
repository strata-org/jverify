package org.strata.jverify.langref.jls9.s9_7;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// JLS 9.7.3 -- Single-Element Annotations: an annotation whose type has a single
// element named "value", allowing the element name to be omitted at the use site;
// the value is read back via reflection at runtime.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls9.s9_7.SingleElementAnnotation#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class SingleElementAnnotation {
    @Retention(RetentionPolicy.RUNTIME)
    @interface Count {
        int value();
    }

    @Count(11)
    static class Target {
    }

    static void run() {
        Count c = Target.class.getAnnotation(Count.class);
        check(c.value() == 11);
    }
}
