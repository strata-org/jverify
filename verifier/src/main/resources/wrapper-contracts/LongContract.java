package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import org.strata.jverify.Transparent;
import static org.strata.jverify.JVerify.*;

// The contract of java.lang.Long, used by both routes. A box is an object with one
// field that holds the primitive. valueOf allocates a new box, so the identity
// of a box is not Java's, which caches some; both routes refuse a reference
// comparison that may be between boxes.
@Contract(value = Long.class, pure = true)
class LongContract {
    public static final long MIN_VALUE = 0x8000000000000000L;
    public static final long MAX_VALUE = 0x7fffffffffffffffL;

    private final long value;

    public LongContract(long value) {
        this.value = value;
    }

    public static LongContract valueOf(long l) {
        postcondition((LongContract boxed) -> boxed.longValue() == l);
        return new LongContract(l);
    }

    @Transparent
    public long longValue() {
        return value;
    }

    @Transparent
    public boolean equals(Object obj) {
        return obj instanceof LongContract ? value == ((LongContract) obj).value : false;
    }
}
