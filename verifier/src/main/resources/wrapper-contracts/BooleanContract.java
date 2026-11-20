package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import org.strata.jverify.Transparent;
import static org.strata.jverify.JVerify.*;

// The contract of java.lang.Boolean, used by both routes. A box is an object with one
// field that holds the primitive. valueOf allocates a new box, so the identity
// of a box is not Java's, which caches some; both routes refuse a reference
// comparison that may be between boxes.
@Contract(value = Boolean.class, pure = true)
class BooleanContract {
    public static final BooleanContract TRUE = new BooleanContract(true);
    public static final BooleanContract FALSE = new BooleanContract(false);

    private final boolean value;

    public BooleanContract(boolean value) {
        this.value = value;
    }

    public static BooleanContract valueOf(boolean b) {
        postcondition((BooleanContract boxed) -> boxed.booleanValue() == b);
        return new BooleanContract(b);
    }

    @Transparent
    public boolean booleanValue() {
        return value;
    }

    @Transparent
    public boolean equals(Object obj) {
        return obj instanceof BooleanContract ? value == ((BooleanContract) obj).value : false;
    }
}
