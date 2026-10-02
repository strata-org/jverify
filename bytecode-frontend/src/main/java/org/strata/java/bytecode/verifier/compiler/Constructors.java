package org.strata.java.bytecode.verifier.compiler;

import org.strata.java.bytecode.frontend.ClassfileQueries;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;

final class Constructors {

    private Constructors() {
    }

    static String procName(String classFqn, MethodTypeDesc desc) {
        var sb = new StringBuilder(classFqn).append("$init");
        for (var pt : desc.parameterList()) {
            sb.append(ClassResolver.MANGLE_SEPARATOR).append(ClassResolver.erasedTypeName(pt));
        }
        return sb.toString();
    }

    static boolean classEligible(ClassModel cm) {
        if (cm.flags().has(AccessFlag.ENUM) || cm.flags().has(AccessFlag.INTERFACE)
                || cm.findAttribute(java.lang.classfile.Attributes.record()).isPresent()) {
            return false;
        }
        boolean generic = cm.findAttribute(java.lang.classfile.Attributes.signature()).map(sa -> {
            try {
                return !java.lang.classfile.ClassSignature.parseFrom(sa.signature().stringValue())
                        .typeParameters().isEmpty();
            } catch (RuntimeException e) {
                return true;
            }
        }).orElse(false);
        if (generic) {
            return false;
        }
        String self = cm.thisClass().asInternalName();
        for (var ic : ClassfileQueries.innerClassesOf(cm)) {
            if (!ic.innerClass().asInternalName().equals(self)) {
                continue;
            }
            if (ic.innerName().isEmpty() || ic.outerClass().isEmpty()) {
                return false;
            }
            return (ic.flagsMask() & ClassFile.ACC_STATIC) != 0;
        }
        return true;
    }

    static boolean eligible(ClassModel cm, MethodModel init) {
        return init.methodName().stringValue().equals("<init>")
                && (init.flags().flagsMask() & (ClassFile.ACC_SYNTHETIC | ClassFile.ACC_BRIDGE)) == 0
                && init.code().isPresent()
                && classEligible(cm)
                && BytecodeToLaurelCompiler.structuralExplicitCtor(cm, init);
    }

    static MethodModel find(ClassModel cm, MethodTypeDesc desc) {
        for (var m : cm.methods()) {
            if (m.methodName().stringValue().equals("<init>")
                    && m.methodTypeSymbol().equals(desc)) {
                return m;
            }
        }
        return null;
    }
}
