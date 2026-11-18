package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import org.strata.jverify.Transparent;
import static org.strata.jverify.JVerify.*;

// The contract of java.lang.Character, used by both routes. A box is an object with one
// field that holds the primitive. valueOf allocates a new box, so the identity
// of a box is not Java's, which caches some; both routes refuse a reference
// comparison that may be between boxes.
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
