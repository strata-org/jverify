package org.strata.java.bytecode.frontend;

import java.lang.classfile.Label;
import java.lang.classfile.Signature;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.LocalVariableType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scoped local-variable table: resolves a slot to a (name, descriptor) using the position, because
 * javac reuses one slot for several variables whose live ranges do not overlap.
 */
public final class Locals {
    public record Scope(int start, int end, String name, String desc, String genericType) { }
    public final Map<Integer,List<Scope>> bySlot = new HashMap<>();
    public void add(int slot, int start, int end, String name, String desc, String genericType) {
        bySlot.computeIfAbsent(slot, k -> new ArrayList<>()).add(new Scope(start, end, name, desc, genericType));
    }
    /** The scope for {@code slot} covering {@code pos} (or the nearest one, to absorb the declaring store). */
    /**
     * The scope for {@code slot} covering {@code pos}, tolerating the store or label immediately before a
     * scope opens (which is where a declaration's value is computed).
     *
     * <p>A scope about to OPEN is preferred over one that has just closed. Both can be within tolerance
     * of the same position when a slot is reused: a catch parameter's scope begins just after the astore
     * that binds it, exactly where the previous occupant's scope ended, and taking the closed one named
     * the exception after the variable it replaced.
     */
    public Scope at(int slot, int pos) {
        List<Scope> ss = bySlot.get(slot);
        if (ss == null) return null;
        Scope fwd = null, back = null;
        int fd = Integer.MAX_VALUE, bd = Integer.MAX_VALUE;
        for (Scope s : ss) {
            if (pos >= s.start() && pos < s.end()) return s;                 // strictly in scope
            if (pos < s.start()) { int d = s.start() - pos; if (d < fd) { fd = d; fwd = s; } }
            else { int d = pos - s.end(); if (d < bd) { bd = d; back = s; } }
        }
        if (fwd != null && fd <= 3) return fwd;
        return (back != null && bd <= 3) ? back : null;
    }
    public static Locals from(List<LocalVariable> lvs, List<LocalVariableType> lvts, Map<Label,Integer> labelPos) {
        // generic local types (LocalVariableTypeTable), keyed by slot@startScope
        Map<String,String> generics = new HashMap<>();
        for (LocalVariableType lvt : lvts) {
            Integer s = labelPos.get(lvt.startScope());
            if (s == null) continue;
            try { generics.put(lvt.slot() + "@" + s, ClassfileQueries.renderSig(Signature.parseFrom(lvt.signature().stringValue()))); }
            catch (RuntimeException ignore) { }
        }
        Locals L = new Locals();
        for (LocalVariable lv : lvs) {
            Integer s = labelPos.get(lv.startScope()), e = labelPos.get(lv.endScope());
            int start = s != null ? s : 0;
            L.add(lv.slot(), start, e != null ? e : Integer.MAX_VALUE,
                    lv.name().stringValue(), lv.type().stringValue(), generics.get(lv.slot() + "@" + start));
        }
        return L;
    }

    /**
     * Declaration-identity key for a local: (slot, name, type). One source variable can have several
     * disjoint LVT liveness scopes (e.g. assigned in each switch case) that share name+type -> same key,
     * so it is declared once. Reused slots with different name/type get different keys.
     */
    public static String slotVarKey(Locals locals, int slot, int pos) {
        if (locals != null) {
            Locals.Scope s = locals.at(slot, pos);
            if (s != null) return slot + ":" + s.name() + ":" + s.desc();
            if (locals.bySlot.containsKey(slot)) return "v" + slot;   // unnamed here (no LVT entry)
        }
        return "v" + slot;
    }
}
