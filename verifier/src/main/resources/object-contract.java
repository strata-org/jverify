package com.aws.jverify.builtin;

import com.aws.jverify.Contract;
import com.aws.jverify.*;

import static com.aws.jverify.JVerify.*;

@Contract(Object.class)
class ObjectContract {
    public ObjectContract() {}
}

@Contract(value = Record.class, immutable = true)
abstract class RecordContract {
//    @Pure
//    @Override
//    public boolean equals(Object obj)
//    {
//        return JVerify.jequals(this, obj);
//    }
}

@Verify(false)
class GhostArray<TArrayElement> {
    public static <TCreateArrayElement> GhostArray<TCreateArrayElement> create(int size) {
        precondition(size >= 0);
        //noinspection ConstantValue
        postcondition((GhostArray<TCreateArrayElement> r) -> r.length() == size && fresh(r));
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
        postcondition(get(index) == value && JVerify.forall(
                (int i) -> implies(i != index && i >= 0, old(get(i)) == get(i))));
    }
}
