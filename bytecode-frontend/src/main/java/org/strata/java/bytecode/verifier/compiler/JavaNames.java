package org.strata.java.bytecode.verifier.compiler;

/**
 * The one place Java class-name forms convert. The compilation uses DOTTED
 * fqns with nesting flattened ({@code pkg.Outer.Inner}): both the JVM's
 * {@code /} package separator and its {@code $} nesting separator map to
 * {@code .}, matching how the names appear in source and in diagnostics.
 */
final class JavaNames {

    private JavaNames() {
    }

    /**
     * The Laurel procedure of static method {@code name} of {@code ownerFqn}. {@code :} appears in no Java
     * identifier and in no name the frontend or Strata generates, so no two procedures share a name.
     */
    static String staticProcedure(String ownerFqn, String name) {
        return ownerFqn + "::" + name;
    }

    /** A JVM internal ({@code pkg/Outer$Inner}) or binary ({@code pkg.Outer$Inner}) name as a dotted fqn. */
    static String dottedFqn(String internalOrBinary) {
        return internalOrBinary.replace('/', '.').replace('$', '.');
    }

    /** The last {@code .}-segment of a dotted name. */
    static String simpleName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }
}
