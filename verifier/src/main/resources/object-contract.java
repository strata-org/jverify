package com.aws.jverify.builtin;

import com.aws.jverify.*;

import static com.aws.jverify.JVerify.*;

import java.util.Iterator;

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
    public static <TCreateArrayElement> JArray<TCreateArrayElement> create(int size) {
        precondition(size >= 0);
        //noinspection ConstantValue
        postcondition((JArray<TCreateArrayElement> r) -> r.length() == size && fresh(r));
        throw new ContractException();
    }

    @Pure
    public @Nat int length() {
        throw new ContractException();
    }

    @Pure
    public TArrayElement get(@Nat int index) {
        reads(this);
        throw new ContractException();
    }

    public void set(@Nat int index, TArrayElement value) {
        modifies(this);
        precondition(index <= length());
        //noinspection ConstantValue
        postcondition(get(index) == value && forall(
                (int i) -> implies(i != index && i >= 0, old(get(i)) == get(i))));
    }
}

@Impure
@Contract(Iterator.class)
abstract class IteratorContract<E> implements Iterator<E> {    
    int remainingEntries;
    
    /**
     * Returns {@code true} if the iteration has more elements.
     * (In other words, returns {@code true} if {@link #next} would
     * return an element rather than throwing an exception.)
     *
     * @return {@code true} if the iteration has more elements
     */
    @Pure
    public boolean hasNext() {
        reads(this);
        return remainingEntries > 0;
    }

    public E next() {
        precondition(hasNext());
        modifies(this);
        postcondition(remainingEntries == old(remainingEntries) - 1);
        throw new ContractException();
    }
    
}


@Contract(Iterable.class)
abstract class IterableContract<T> implements Iterable<T> {    
    public Iterator<T> iterator() {
        postcondition((Iterator<T> r) -> fresh(r));
        throw new ContractException();
    }
}
