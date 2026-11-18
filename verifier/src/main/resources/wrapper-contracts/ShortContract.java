package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import org.strata.jverify.Transparent;
import static org.strata.jverify.JVerify.*;

// The contract of java.lang.Short, used by both routes. A box is an object with one
// field that holds the primitive. valueOf allocates a new box, so the identity
// of a box is not Java's, which caches some; both routes refuse a reference
// comparison that may be between boxes.
@Contract(value = Short.class, pure = true)
class ShortContract {
    public static final short MIN_VALUE = -32768;
    public static final short MAX_VALUE = 32767;

    private final short value;

    public ShortContract(short value) {
        this.value = value;
    }

    public static ShortContract valueOf(short s) {
        postcondition((ShortContract boxed) -> boxed.shortValue() == s);
        return new ShortContract(s);
    }

    @Transparent
    public short shortValue() {
        return value;
    }

    @Transparent
    public boolean equals(Object obj) {
        return obj instanceof ShortContract ? value == ((ShortContract) obj).value : false;
    }
}
