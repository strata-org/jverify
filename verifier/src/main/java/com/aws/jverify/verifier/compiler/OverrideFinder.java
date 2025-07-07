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
    public static Symbol.MethodSymbol findOverriddenMethod(Symbol.MethodSymbol method, Types types) {
        Symbol.ClassSymbol owner = (Symbol.ClassSymbol) method.owner;

        Symbol.MethodSymbol result = findInSuperClasses(method, owner, types);
        if (result != null) {
            return result;
        }

        return findInInterfaces(method, owner, types);
    }

    private static Symbol.MethodSymbol findInSuperClasses(Symbol.MethodSymbol method, Symbol.ClassSymbol owner, Types types) {
        Type currentType = owner.getSuperclass();

        while (currentType != null && currentType.tsym instanceof Symbol.ClassSymbol currentClass) {

            for (Symbol member : currentClass.members().getSymbolsByName(method.name)) {
                if (member instanceof Symbol.MethodSymbol candidate) {
                    if (method.overrides(candidate, owner, types, true)) {
                        return candidate;
                    }
                }
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
            
            for (Symbol member : interfaceClass.members().getSymbolsByName(method.name)) {
                if (member instanceof Symbol.MethodSymbol candidate) {
                    if (method.overrides(candidate, owner, types, true)) {
                        return candidate;
                    }
                }
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
}
