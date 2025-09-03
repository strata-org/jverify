package com.aws.jverify.builtin;

import com.aws.jverify.Contract;
import com.aws.jverify.Verify;
import com.aws.jverify.Pure;
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
class RecordContract {}