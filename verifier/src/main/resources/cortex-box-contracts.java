package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import org.strata.jverify.Transparent;
import static org.strata.jverify.JVerify.*;

// The contracts of the JDK's wrapper classes that the direct Cortex translation
// runs when interpreting: a call to a member that has a body here runs that
// body. A box is an object with one field that holds the primitive. Boxes are
// not cached, so the identity of a box is not Java's.

@Contract(value = Integer.class, pure = true)
class IntegerContract {
    private final int value;

    public IntegerContract(int value) {
        this.value = value;
    }

    public static IntegerContract valueOf(int i) {
        postcondition((IntegerContract boxed) -> boxed.intValue() == i);
        return new IntegerContract(i);
    }

    @Transparent
    public int intValue() {
        return value;
    }

    @Transparent
    public boolean equals(Object obj) {
        return obj instanceof IntegerContract ? value == ((IntegerContract) obj).value : false;
    }
}

@Contract(value = Long.class, pure = true)
class LongContract {
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

@Contract(value = Short.class, pure = true)
class ShortContract {
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

@Contract(value = Character.class, pure = true)
class CharacterContract {
    private final char value;

    public CharacterContract(char value) {
        this.value = value;
    }

    public static CharacterContract valueOf(char c) {
        postcondition((CharacterContract boxed) -> boxed.charValue() == c);
        return new CharacterContract(c);
    }

    @Transparent
    public char charValue() {
        return value;
    }

    @Transparent
    public boolean equals(Object obj) {
        return obj instanceof CharacterContract ? value == ((CharacterContract) obj).value : false;
    }
}

@Contract(value = Boolean.class, pure = true)
class BooleanContract {
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
