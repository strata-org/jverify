package com.aws.jverify.builtin;

import com.aws.jverify.*;
import static com.aws.jverify.JVerify.*;
import java.math.BigInteger;
import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import static com.aws.jverify.JVerify.check;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Function;
import java.util.function.Consumer;

import java.util.Collection;
import java.util.List;
import java.util.SequencedCollection;

@Contract(Object.class)
class ObjectContract {
    public ObjectContract() {}
}
