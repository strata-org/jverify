package com.aws.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A sealed interface annotated with {@link Union} is treated by the verifier
 * as having only the permitted subclasses
 * (as opposed to having arbitrary, possibly externally-defined subclasses).
 * <p>
 * Note that all subclasses of a {@link Union} sealed interface must be records.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Union {
}
