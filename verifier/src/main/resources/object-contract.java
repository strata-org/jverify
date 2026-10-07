package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import static org.strata.jverify.JVerify.*;
import org.strata.jverify.Nat;
import org.strata.jverify.Unbounded;
import org.strata.jverify.JVerify;
import org.strata.jverify.Verify;
import org.strata.jverify.Pure;
import org.strata.jverify.ContractException;

@Contract(Object.class)
class ObjectContract {
    public ObjectContract() {}

    @Pure
    public boolean equals(Object obj) {
        throw new ContractException();
    }
}

@Contract(value = Record.class, pure = true)
class RecordContract {
    @Pure
    public boolean equals(Object obj) {
        return jequals(this, obj);
    }
}

@Contract(value = String.class, pure = true)
class StringContract {
    JVerify.CharJSequence chars;

    @Pure
    @Override
    public boolean equals(Object obj) {
        return obj instanceof StringContract
                ? JVerify.jequals(this, (StringContract)obj)
                : false;
    }

    @Pure
    public StringContract concat(StringContract str) {
        postcondition((StringContract r) -> jequals(r.chars, chars.concat(str.chars)));
        throw new ContractException();
    }

    @Pure
    public int indexOf(int ch) {
        postcondition((int result) -> -1 <= result && result < this.chars.size());
        postcondition((int result) -> result == -1
                ? JVerify.forall((int i) -> JVerify.implies(0 <= i && i < chars.size(), chars.get(i) != ch))
                : chars.get(result) == ch && JVerify.forall((int j) -> JVerify.implies(0 <= j && j < result, chars.get(j) != ch))
        );
        throw new ContractException();
    }

    @Pure
    public int length() {
        postcondition((int result) -> result == chars.size());
        throw new ContractException();
    }

    @Pure
    public char charAt(int index) {
        precondition(0 <= index && index < chars.size());
        return chars.get(index);
    }

    @Pure
    public boolean isEmpty() {
        return chars.size() == 0;
    }

    @Pure
    public StringContract substring(int beginIndex) {

        precondition(beginIndex >= 0 && beginIndex <= chars.size());
        postcondition((StringContract r) -> JVerify.jequals(r.chars, chars.drop(beginIndex)));
        throw new ContractException();
    }

    @Pure
    public String substring(int beginIndex, int endIndex) {
        precondition(beginIndex >= 0 && beginIndex <= endIndex && endIndex <= chars.size());
        postcondition((StringContract r) -> JVerify.jequals(r.chars, chars.subsequence(beginIndex, endIndex)));
        throw new ContractException();
    }

    @Pure
    public boolean startsWith(StringContract prefix) {
        return startsWith(prefix, 0);
    }

    @Pure
    public boolean startsWith(StringContract prefix, int toffset) {
        postcondition((boolean r) -> r == (toffset >= 0 &&
                prefix.chars.size() + toffset <= chars.size() &&
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
    @Pure
    public @Unbounded int length() {
        //noinspection ConstantValue
        return lengthOf(this) < 0 ? 0 : (lengthOf(this) > 2147483647 ? 0 : lengthOf(this));
    }

    @Verify
    @Pure
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

    @Pure
    private static <E> @Unbounded int lengthOf(JArray<E> array) {
        throw new ContractException();
    }

    @Pure
    private static <E> E elementAt(JArray<E> array, @Unbounded int contentsVersion, @Nat int index) {
        throw new ContractException();
    }
}
