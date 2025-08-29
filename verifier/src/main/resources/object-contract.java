package com.aws.jverify.builtin;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.JVerify;
import com.aws.jverify.Pure;

import static com.aws.jverify.JVerify.jequals;
import static com.aws.jverify.JVerify.postcondition;
import static com.aws.jverify.JVerify.precondition;

@Contract(Object.class)
class ObjectContract {
    public ObjectContract() {}
}

@Contract(value = Record.class, immutable = true)
class RecordContract {
    @Pure
    @Override
    public boolean equals(Object obj) {
        return JVerify.jequals(this, obj);
    }
}

@Contract(value = String.class, immutable = true)
class StringContract {
    JVerify.CharJSequence chars;

    @Pure
    public String concat(StringContract str) {
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
        postcondition((StringContract r) -> r.chars == chars.drop(beginIndex));
        throw new ContractException();
    }

    @Pure
    public String substring(int beginIndex, int endIndex) {
        precondition(beginIndex >= 0 && beginIndex <= endIndex && endIndex <= chars.size());
        postcondition((StringContract r) -> r.chars == chars.subsequence(beginIndex, endIndex));
        throw new ContractException();
    }

//    @Pure
//    public boolean equals(Object anObject) {
//        return anObject instanceof StringContract otherString &&
//                chars.equals(otherString.chars);
//    }

    @Pure
    public boolean startsWith(StringContract prefix) {
        precondition(prefix.chars.size() <= chars.size());
        return startsWith(prefix, 0);
    }

    @Pure
    public boolean startsWith(StringContract prefix, int toffset) {
        precondition(toffset >= 0);
        precondition(prefix.chars.size() + toffset <= chars.size());
        postcondition((boolean r) -> r == jequals(
                chars.drop(toffset).take(prefix.chars.size()), 
                prefix.chars)
        );
        throw new ContractException();
    }
}