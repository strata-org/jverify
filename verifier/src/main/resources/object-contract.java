package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import static org.strata.jverify.JVerify.*;
import org.strata.jverify.Nat;
import org.strata.jverify.Unbounded;
import org.strata.jverify.JVerify;
import org.strata.jverify.Verify;
import org.strata.jverify.Transparent;
import org.strata.jverify.ContractException;

@Contract(Object.class)
class ObjectContract {
    public ObjectContract() {}

    @Transparent
    public boolean equals(Object obj) {
        throw new ContractException();
    }
}

@Contract(value = Record.class, pure = true)
class RecordContract {
    @Transparent
    public boolean equals(Object obj) {
        return jequals(this, obj);
    }
}

@Contract(value = String.class, pure = true)
class StringContract {
    JVerify.CharJSequence chars;

    @Transparent
    @Override
    public boolean equals(Object obj) {
        return obj instanceof StringContract
                ? JVerify.jequals(this, (StringContract)obj)
                : false;
    }

    public StringContract concat(StringContract str) {
        postcondition((StringContract r) -> jequals(r.chars, chars.concat(str.chars)));
        throw new ContractException();
    }

    public int indexOf(int ch) {
        postcondition((int result) -> -1 <= result && result < length());
        postcondition((int result) -> result == -1
                ? JVerify.forall((int i) -> JVerify.implies(0 <= i && i < length(), chars.get(i) != ch))
                : chars.get(result) == ch && JVerify.forall((int j) -> JVerify.implies(0 <= j && j < result, chars.get(j) != ch))
        );
        throw new ContractException();
    }

    /**
     * A function of the string's characters, so two calls on the same string
     * agree. The clamp makes it a valid Java length (0..Integer.MAX_VALUE) by
     * definition; an {@code int} result would be refused as a contract body.
     * The other members bound indexes by {@code length()}, not by
     * {@code chars.size()}, so they agree with it even where the clamp applies.
     */
    @Transparent
    public @Unbounded int length() {
        //noinspection ConstantValue
        return chars.size() < 0 ? 0 : (chars.size() > 2147483647 ? 0 : chars.size());
    }

    @Transparent
    public char charAt(int index) {
        precondition(0 <= index && index < length());
        return chars.get(index);
    }

    @Transparent
    public boolean isEmpty() {
        return length() == 0;
    }

    public StringContract substring(int beginIndex) {

        precondition(beginIndex >= 0 && beginIndex <= length());
        postcondition((StringContract r) -> JVerify.jequals(r.chars, chars.drop(beginIndex)));
        throw new ContractException();
    }

    public String substring(int beginIndex, int endIndex) {
        precondition(beginIndex >= 0 && beginIndex <= endIndex && endIndex <= length());
        postcondition((StringContract r) -> JVerify.jequals(r.chars, chars.subsequence(beginIndex, endIndex)));
        throw new ContractException();
    }

    @Transparent
    public boolean startsWith(StringContract prefix) {
        return startsWith(prefix, 0);
    }

    public boolean startsWith(StringContract prefix, int toffset) {
        postcondition((boolean r) -> r == (toffset >= 0 &&
                prefix.length() + toffset <= length() &&
                jequals(
                        chars.drop(toffset).take(prefix.chars.size()),
                        prefix.chars)
        ));
        throw new ContractException();
    }
}

@Verify(false)
class JArray<TArrayElement> {
    /**
     * Ghost state: the version of this array's contents. A write replaces it
     * ({@code set} modifies {@code this}), and {@code get} reads it, so an element
     * read depends on this array's state and on nothing else.
     */
    @Unbounded int contentsVersion;

    public static <TCreateArrayElement> JArray<TCreateArrayElement> create(int size) {
        precondition(size >= 0);
        //noinspection ConstantValue
        postcondition((JArray<TCreateArrayElement> r) -> r.length() == size && fresh(r));
        throw new ContractException();
    }

    /**
     * An array's length never changes, so it is a function of the array alone.
     * The clamp makes it a valid Java length (0..Integer.MAX_VALUE) by definition.
     */
    @Verify
    @Transparent
    public @Unbounded int length() {
        //noinspection ConstantValue
        return lengthOf(this) < 0 ? 0 : (lengthOf(this) > 2147483647 ? 0 : lengthOf(this));
    }

    @Verify
    @Transparent
    public TArrayElement get(@Nat int index) {
        reads(this);
        precondition(index < length());
        return elementAt(this, contentsVersion, index);
    }

    public void set(@Nat int index, TArrayElement value) {
        modifies(this);
        precondition(0 <= index && index < length());
        //noinspection ConstantValue
        postcondition(get(index) == value && forall(
                (int i) -> implies(i != index && 0 <= i && i < length(), old(get(i)) == get(i))));
    }

    @Transparent
    private static <E> @Unbounded int lengthOf(JArray<E> array) {
        throw new ContractException();
    }

    @Transparent
    private static <E> E elementAt(JArray<E> array, @Unbounded int contentsVersion, @Nat int index) {
        throw new ContractException();
    }
}

@Contract(java.util.Objects.class)
class ObjectsContract {
    @Transparent
    public static <T> T requireNonNull(T obj) {
        return obj;
    }
}
