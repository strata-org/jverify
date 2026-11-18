package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import org.strata.jverify.Transparent;
import static org.strata.jverify.JVerify.*;

// The contract of java.lang.Byte, used by both routes. A box is an object with one
// field that holds the primitive. valueOf allocates a new box, so the identity
// of a box is not Java's, which caches some; both routes refuse a reference
// comparison that may be between boxes.
@Contract(value = Byte.class, pure = true)
class ByteContract {
    private final byte value;

    public ByteContract(byte value) {
        this.value = value;
    }

    public static ByteContract valueOf(byte b) {
        postcondition((ByteContract boxed) -> boxed.byteValue() == b);
        return new ByteContract(b);
    }

    @Transparent
    public byte byteValue() {
        return value;
    }

    @Transparent
    public boolean equals(Object obj) {
        return obj instanceof ByteContract ? value == ((ByteContract) obj).value : false;
    }
}
