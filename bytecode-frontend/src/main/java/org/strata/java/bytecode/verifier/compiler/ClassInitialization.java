package org.strata.java.bytecode.verifier.compiler;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The JVM's class initialization rules, over the program's class files
 * (JVM specification, section 5.5), for the translations of static fields.
 *
 * <p>A class is initialized the first time it is used: at a {@code new}, at
 * a read or write of one of its static fields, or at a call to one of its
 * static methods. Before a class is initialized, its superclass is, and so
 * are its superinterfaces that declare a default method. Initializing an
 * interface initializes nothing else. The initialization runs the class's
 * {@code <clinit>}.
 *
 * <p>javac adds a {@code <clinit>} that only sets {@code $assertionsDisabled}
 * to every class that contains an {@code assert}, and one that fills the
 * {@code $VALUES} array to every enum. Assertions are translated as always
 * enabled and {@code values()} is not translated, so such assignments are
 * dropped, and a {@code <clinit>} with nothing else in it has no effect on
 * the translation and counts as absent. Any other instruction makes it an
 * initializer, since it may have an effect or fail.
 */
final class ClassInitialization {

    private ClassInitialization() {
    }

    /** The {@code <clinit>} of program class {@code fqn} if it does anything the translation keeps, else null. */
    static MethodModel initializerOf(ClassResolver resolver, String fqn) {
        var cm = resolver.programClassModels.get(fqn);
        if (cm == null) {
            return null;
        }
        for (var m : cm.methods()) {
            if (m.methodName().stringValue().equals("<clinit>")) {
                return onlySetsSyntheticFields(resolver, m) ? null : m;
            }
        }
        return null;
    }

    /**
     * True iff {@code clinit} consists of nothing but javac's two patterns:
     * {@code $assertionsDisabled = !C.class.desiredAssertionStatus()} and
     * {@code $VALUES = $values()}. Anything else counts as an initializer, so
     * {@code 1 / z}, an array allocation, a cast or a loop is one.
     */
    private static boolean onlySetsSyntheticFields(ClassResolver resolver, MethodModel clinit) {
        var code = clinit.code().orElse(null);
        if (code == null) {
            return true;
        }
        for (var el : code.elementList()) {
            boolean allowed = switch (el) {
                case FieldInstruction fi -> fi.opcode() == Opcode.PUTSTATIC
                        && (isAssertionsDisabled(resolver, fi) || isEnumValues(resolver, fi));
                case InvokeInstruction inv -> isDesiredAssertionStatus(inv) || isEnumValuesMethod(resolver, inv);
                case ConstantInstruction c -> c.constantValue() instanceof ClassDesc
                        || c.constantValue() instanceof Integer;
                case BranchInstruction b -> b.opcode() == Opcode.IFNE || b.opcode() == Opcode.GOTO;
                case ReturnInstruction r -> true;
                case Instruction i -> false;
                default -> true;   // labels, line numbers, local variable tables
            };
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    /**
     * True iff the static fields of {@code fqn} hold their defaults when its
     * {@code <clinit>} starts: no type the JVM initializes before it
     * (transitively) runs an initializer. Such an initializer runs after
     * {@code fqn} is marked in progress (JLS 12.4.2), so a write to a static
     * field of {@code fqn} from it does not start the initialization again
     * and lands before {@code <clinit>}.
     */
    static boolean staticsAtDefaultsInInitializer(ClassResolver resolver, String fqn) {
        for (String t : initializationStarted(resolver, fqn)) {
            if (!t.equals(fqn) && initializerOf(resolver, t) != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code Class.desiredAssertionStatus()} where javac emits it: in the
     * {@code <clinit>} of a class that has a {@code $assertionsDisabled} field,
     * computing that field. Elsewhere the call is an ordinary library call.
     */
    static boolean isAssertionStatusProbe(ClassResolver resolver, ClassModel cm, String methodName,
                                          InvokeInstruction inv) {
        if (!isDesiredAssertionStatus(inv) || !methodName.equals("<clinit>")) {
            return false;
        }
        for (var f : cm.fields()) {
            if (f.fieldName().stringValue().equals("$assertionsDisabled")
                    && (f.flags().flagsMask() & ClassFile.ACC_SYNTHETIC) != 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * The program class that declares the static method {@code name} with
     * descriptor {@code desc} that a call through {@code fqn} names: {@code fqn}
     * or a superclass (JLS 12.4.1: the call initializes the declaring class,
     * not the class named in the instruction). Null if none in the program.
     */
    static String declaringClassOfStaticMethod(ClassResolver resolver, String fqn, String name, String desc) {
        for (String c = fqn; c != null; c = resolver.programSuper.get(c)) {
            var cm = resolver.programClassModels.get(c);
            if (cm == null) {
                return null;
            }
            for (var m : cm.methods()) {
                if (m.methodName().stringValue().equals(name) && m.methodTypeSymbol().descriptorString().equals(desc)
                        && (m.flags().flagsMask() & ClassFile.ACC_STATIC) != 0) {
                    return c;
                }
            }
        }
        return null;
    }

    /** True iff {@code fi} accesses a synthetic field named {@code name} of a program class. */
    private static boolean isSyntheticField(ClassResolver resolver, FieldInstruction fi, String name) {
        if (!fi.name().stringValue().equals(name)) {
            return false;
        }
        var cm = resolver.programClassModels.get(JavaNames.dottedFqn(fi.owner().asInternalName()));
        if (cm == null) {
            return false;
        }
        for (var f : cm.fields()) {
            if (f.fieldName().stringValue().equals(name)) {
                return (f.flags().flagsMask() & ClassFile.ACC_SYNTHETIC) != 0;
            }
        }
        return false;
    }

    /** javac's {@code $assertionsDisabled}, in a class that contains an {@code assert}. */
    static boolean isAssertionsDisabled(ClassResolver resolver, FieldInstruction fi) {
        return isSyntheticField(resolver, fi, "$assertionsDisabled");
    }

    /** The synthetic {@code $VALUES} field of an enum. */
    static boolean isEnumValues(ClassResolver resolver, FieldInstruction fi) {
        return isSyntheticField(resolver, fi, "$VALUES");
    }

    /** The synthetic {@code $values()} method of a program enum, which fills {@code $VALUES}. */
    static boolean isEnumValuesMethod(ClassResolver resolver, InvokeInstruction inv) {
        if (!inv.name().stringValue().equals("$values") || inv.opcode() != java.lang.classfile.Opcode.INVOKESTATIC) {
            return false;
        }
        var cm = resolver.programClassModels.get(JavaNames.dottedFqn(inv.owner().asInternalName()));
        return cm != null && cm.flags().has(AccessFlag.ENUM);
    }

    /** {@code Class.desiredAssertionStatus()}, which computes {@code $assertionsDisabled}. */
    static boolean isDesiredAssertionStatus(InvokeInstruction inv) {
        return inv.owner().asInternalName().equals("java/lang/Class")
                && inv.name().stringValue().equals("desiredAssertionStatus");
    }

    /**
     * The program class or interface that declares the non-synthetic static
     * field {@code name} that an access through {@code fqn} names: {@code fqn}
     * itself, a superclass, or a superinterface. Null if none in the program.
     */
    static String declaringClassOfStatic(ClassResolver resolver, String fqn, String name) {
        var cm = resolver.programClassModels.get(fqn);
        if (cm == null) {
            return null;
        }
        for (var f : cm.fields()) {
            int flags = f.flags().flagsMask();
            if (f.fieldName().stringValue().equals(name) && (flags & ClassFile.ACC_STATIC) != 0
                    && (flags & ClassFile.ACC_SYNTHETIC) == 0) {
                return fqn;
            }
        }
        // JVMS 5.4.3.2: the direct superinterfaces, recursively, before the superclass.
        for (String i : resolver.directInterfacesOf(fqn)) {
            String c = declaringClassOfStatic(resolver, i, name);
            if (c != null) {
                return c;
            }
        }
        String sup = resolver.programSuper.get(fqn);
        return sup == null ? null : declaringClassOfStatic(resolver, sup, name);
    }

    /**
     * True iff program interface {@code fqn} declares a default method: a
     * non-private instance method with a body. A private interface method is
     * not a default method and triggers no initialization (JLS 12.4.1).
     */
    private static boolean declaresDefaultMethod(ClassResolver resolver, String fqn) {
        var cm = resolver.programClassModels.get(fqn);
        if (cm == null || !cm.flags().has(AccessFlag.INTERFACE)) {
            return false;
        }
        for (var m : cm.methods()) {
            int flags = m.flags().flagsMask();
            if ((flags & (ClassFile.ACC_STATIC | ClassFile.ACC_ABSTRACT | ClassFile.ACC_PRIVATE)) == 0
                    && !m.methodName().stringValue().equals("<clinit>")) {
                return true;
            }
        }
        return false;
    }

    /**
     * The program types the JVM initializes before it initializes {@code fqn},
     * in that order (JVMS 5.5, step 7): for a class, its superclass, then its
     * superinterfaces that declare a default method, each after its own
     * superinterfaces, in the order of the {@code interfaces} arrays; for an
     * interface, nothing.
     */
    static List<String> initializedBefore(ClassResolver resolver, String fqn) {
        var out = new ArrayList<String>();
        var cm = resolver.programClassModels.get(fqn);
        if (cm == null || cm.flags().has(AccessFlag.INTERFACE)) {
            return out;
        }
        String sup = resolver.programSuper.get(fqn);
        if (sup != null && resolver.programClassModels.containsKey(sup)) {
            out.add(sup);
        }
        var seen = new HashSet<String>();
        for (String i : resolver.directInterfacesOf(fqn)) {
            addSuperinterfacesPostOrder(resolver, i, seen, out);
        }
        return out;
    }

    private static void addSuperinterfacesPostOrder(ClassResolver resolver, String i, Set<String> seen,
                                                    List<String> out) {
        if (!seen.add(i)) {
            return;
        }
        for (String s : resolver.directInterfacesOf(i)) {
            addSuperinterfacesPostOrder(resolver, s, seen, out);
        }
        if (declaresDefaultMethod(resolver, i)) {
            out.add(i);
        }
    }

    /**
     * The program types whose initialization has started when code of
     * {@code currentClass} runs: that class and, transitively, the types
     * initialized before it. An instance or a running static method of a
     * class exists only after the initialization of that class has started.
     */
    static Set<String> initializationStarted(ClassResolver resolver, String currentClass) {
        var started = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        if (currentClass != null) {
            queue.add(currentClass);
        }
        while (!queue.isEmpty()) {
            String c = queue.poll();
            if (started.add(c)) {
                queue.addAll(initializedBefore(resolver, c));
            }
        }
        return started;
    }

    /**
     * True iff initializing {@code fqn} runs some initializer not yet run: its
     * own, or one of a type initialized before it.
     */
    static boolean runsInitializer(ClassResolver resolver, String fqn, Set<String> started) {
        if (started.contains(fqn)) {
            return false;
        }
        if (initializerOf(resolver, fqn) != null) {
            return true;
        }
        for (String before : initializedBefore(resolver, fqn)) {
            if (runsInitializer(resolver, before, started)) {
                return true;
            }
        }
        return false;
    }
}
