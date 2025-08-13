package com.aws.jverify.verifier.compiler;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OverrideFinder 
{
    public static Symbol.MethodSymbol findOverriddenMethod(Symbol.ClassSymbol contractee, Symbol.MethodSymbol method, Types types) {
        Symbol.MethodSymbol candidate = getCandidateForType(contractee, method, types);
        if (candidate != null) {
            return candidate;
        }

        Symbol.MethodSymbol result = findInSuperClasses(method, contractee, types);
        if (result != null) {
            return result;
        }

        return findInInterfaces(method, contractee, types);
    }

    private static Symbol.MethodSymbol findInSuperClasses(Symbol.MethodSymbol method, Symbol.ClassSymbol owner, Types types) {
        Type currentType = owner.getSuperclass();

        while (currentType != null && currentType.tsym instanceof Symbol.ClassSymbol currentClass) {

            Symbol.MethodSymbol candidate = getCandidateForType(currentClass, method, types);
            if (candidate != null) {
                return candidate;
            }

            currentType = currentClass.getSuperclass();
        }

        return null;
    }

    private static Symbol.MethodSymbol findInInterfaces(Symbol.MethodSymbol method, Symbol.ClassSymbol owner, Types types) {
        for (Type interfaceType : getAllInterfaces(owner, types)) {
            if (!(interfaceType.tsym instanceof Symbol.ClassSymbol interfaceClass)) {
                continue;
            }

            Symbol.MethodSymbol candidate = getCandidateForType(interfaceClass, method, types);
            if (candidate != null) {
                return candidate;
            }
        }

        return null;
    }

    private static List<Type> getAllInterfaces(Symbol.ClassSymbol clazz, Types types) {
        List<Type> result = new ArrayList<>();
        collectInterfaces(clazz.type, result, new HashSet<Type>(), types);
        return result;
    }

    private static void collectInterfaces(Type type, List<Type> result, Set<Type> visited, Types types) {
        if (type == null || visited.contains(type)) return;
        visited.add(type);

        if (type.tsym instanceof Symbol.ClassSymbol clazz) {
            for (Type interfaceType : clazz.getInterfaces()) {
                result.add(interfaceType);
                collectInterfaces(interfaceType, result, visited, types);
            }

            collectInterfaces(clazz.getSuperclass(), result, visited, types);
        }
    }

    private static Symbol.MethodSymbol getCandidateForType(Symbol.ClassSymbol contractee, Symbol.MethodSymbol method, Types types) {
        for (Symbol member : contractee.members().getSymbolsByName(method.name)) {
            if (member instanceof Symbol.MethodSymbol candidate) {
                if (types.isSubSignature(types.erasure(member.type), types.erasure(method.type))) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
