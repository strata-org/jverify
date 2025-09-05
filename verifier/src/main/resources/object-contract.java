package com.aws.jverify.builtin;

import static com.aws.jverify.JVerify.*;
import com.aws.jverify.Contract;
import com.aws.jverify.Verify;
import com.aws.jverify.Pure;
import com.aws.jverify.Nat;
import com.aws.jverify.ContractException;

@Contract(Object.class)
class ObjectContract {
    public ObjectContract() {}

    @Pure
    public boolean equals(Object obj) {
        throw new ContractException();
    }
}

@Contract(value = Record.class, immutable = true)
class RecordContract {
    @Pure
    public boolean equals(Object obj)
    {
        return jequals(this, obj);
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
