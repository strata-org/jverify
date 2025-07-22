package com.aws.jverify.builtin;

import com.aws.jverify.Contract;

@Contract(Object.class)
class ObjectContract {
    public ObjectContract() {}
}

@Contract(value = Record.class, immutable = true)
class RecordConstract {
}