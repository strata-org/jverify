package org.strata.jverify.examples;

import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.strata.jverify.AsProperty;
import org.strata.jverify.Pure;
import static org.strata.jverify.JVerify.*;

public class AbsPropertyGenerated extends AbsProperty {

    // Generated from AbsProperty.java by contracts2jqwik. Do not edit by hand;
    // regenerate by re-running contracts2jqwik on the source file.
    @Property
    public boolean absProperty(@ForAll int x) {
        Assume.that(x != Integer.MIN_VALUE);
        int r = abs(x);
        return (r >= 0 && (r == x || r == -x));
    }
}
