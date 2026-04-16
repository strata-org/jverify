package com.aws.jverify.builtin;

import com.aws.jverify.Contract;
import java.util.function.Predicate;

@Contract
abstract class PredicateContract<T> implements Predicate<T> {
}