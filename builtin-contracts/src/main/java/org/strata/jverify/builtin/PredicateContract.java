package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import java.util.function.Predicate;

@Contract
abstract class PredicateContract<T> implements Predicate<T> {
}