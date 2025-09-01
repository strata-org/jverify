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
class StringTypeContract {
    JVerify.CharJSequence chars;
}