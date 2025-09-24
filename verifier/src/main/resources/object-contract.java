package com.aws.jverify.builtin;

import com.aws.jverify.Contract;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.Nat;
import com.aws.jverify.JVerify;
import com.aws.jverify.Verify;
import com.aws.jverify.Pure;
import com.aws.jverify.ContractException;

@Contract(Object.class)
class ObjectContract {
    public ObjectContract() {}

    @Pure
    public boolean equals(@Pure Object obj) {
        throw new ContractException();
    }
}
@Pure
@Contract(value = Record.class)
class RecordContract {
    @Pure
    public boolean equals(@Pure Object obj) {
        return jequals(this, obj);
    }
}
@Pure
@Contract(value = String.class)
class StringContract {
    JVerify.CharJSequence chars;

    @Pure
    @Override
    public boolean equals(@Pure Object obj) {
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
