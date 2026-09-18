package org.strata.java.bytecode.frontend;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.attribute.CodeAttribute;

/**
 * What the classfiles reveal about how the input was compiled, as observed
 * attribute EVIDENCE: a fact is true if any inspected class shows the
 * attribute (absence on one class proves nothing -- a method may simply have
 * no locals or no code).
 *
 * <p>The derived {@link Tier} drives user messaging only; the translator's
 * degradation is decided per method from the attributes actually present.
 */
public record CompilationFacts(
        boolean hasCrt, boolean hasLocalVariableTable,
        boolean hasLineNumberTable, boolean hasSourceFile) {

    /** The supported input tiers, best first. */
    public enum Tier {
        /** {@code -g -Xjcov}: statement spans; full-precision diagnostics. */
        FULL,
        /** {@code -g}: local names but no statement spans; method-precision diagnostics. */
        DEBUG,
        /** default javac (no flags): line numbers only; method-precision diagnostics, synthetic local names. */
        BARE,
        /** {@code -g:none} or stripped: no debug information at all. */
        NONE
    }

    public Tier tier() {
        if (hasCrt) {
            return Tier.FULL;
        }
        if (hasLocalVariableTable) {
            return Tier.DEBUG;
        }
        if (hasLineNumberTable || hasSourceFile) {
            return Tier.BARE;
        }
        return Tier.NONE;
    }

    /** The facts one classfile shows. */
    public static CompilationFacts of(ClassModel cm) {
        boolean crt = false, lvt = false, lnt = false;
        for (var m : cm.methods()) {
            var code = m.findAttribute(Attributes.code()).orElse(null);
            if (code == null) {
                continue;
            }
            var ca = (CodeAttribute) code;
            crt |= ca.findAttribute(Attributes.characterRangeTable()).isPresent();
            lvt |= ca.findAttribute(Attributes.localVariableTable()).isPresent();
            lnt |= ca.findAttribute(Attributes.lineNumberTable()).isPresent();
        }
        return new CompilationFacts(crt, lvt, lnt,
                cm.findAttribute(Attributes.sourceFile()).isPresent());
    }

    /** Evidence-OR of two observations. */
    public CompilationFacts or(CompilationFacts other) {
        return new CompilationFacts(
                hasCrt || other.hasCrt,
                hasLocalVariableTable || other.hasLocalVariableTable,
                hasLineNumberTable || other.hasLineNumberTable,
                hasSourceFile || other.hasSourceFile);
    }

    public static final CompilationFacts NONE_OBSERVED =
            new CompilationFacts(false, false, false, false);
}
