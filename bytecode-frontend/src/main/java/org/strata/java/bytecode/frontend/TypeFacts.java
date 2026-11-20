package org.strata.java.bytecode.frontend;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

/**
 * What javac knew of a class outside the JDK that a compilation's classfiles
 * mention, as the translation asks it: names are fqns with {@code $} as {@code .}.
 *
 * @param superclass null for an interface, or for a class the classpath lacks
 * @param methods the methods the class declares, synthetic ones excluded
 */
public record TypeFacts(@Nullable String superclass, List<String> interfaces, boolean isInterface,
                        List<Method> methods) {

    /**
     * @param params the erased parameter descriptors, as a list's string
     *        ({@code [I, Ljava/lang/String;]})
     * @param inheritable neither static nor private
     * @param isAbstract declared abstract
     */
    public record Method(String name, String params, boolean inheritable, boolean isAbstract) {
    }
}
