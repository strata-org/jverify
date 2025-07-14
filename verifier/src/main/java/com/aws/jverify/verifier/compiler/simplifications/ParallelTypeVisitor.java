package com.aws.jverify.verifier.compiler.simplifications;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeMetadata;
import com.sun.tools.javac.code.Types;

import java.util.ArrayList;
import java.util.List;

class ParallelTypeVisitor extends Types.SimpleVisitor<Type, Type> {


    @Override
    public Type visitMethodType(Type.MethodType genericType, Type instantiatedType) {
        if (!(instantiatedType instanceof Type.MethodType)) {
            return instantiatedType;
        }

        Type.MethodType instantiatedMethod = (Type.MethodType) instantiatedType;

        // Process return type
        Type newReturnType = visit(genericType.getReturnType(), instantiatedMethod.getReturnType());

        // Process parameter types
        List<Type> newParamTypes = new ArrayList<>();
        List<Type> genericParams = genericType.getParameterTypes();
        List<Type> instantiatedParams = instantiatedMethod.getParameterTypes();

        for (int i = 0; i < Math.min(genericParams.size(), instantiatedParams.size()); i++) {
            newParamTypes.add(visit(genericParams.get(i), instantiatedParams.get(i)));
        }

        // Process thrown types
        List<Type> newThrownTypes = new ArrayList<>();
        List<Type> genericThrown = genericType.getThrownTypes();
        List<Type> instantiatedThrown = instantiatedMethod.getThrownTypes();

        for (int i = 0; i < Math.min(genericThrown.size(), instantiatedThrown.size()); i++) {
            newThrownTypes.add(visit(genericThrown.get(i), instantiatedThrown.get(i)));
        }

        // Create new MethodType with processed types
        Type.MethodType result = new Type.MethodType(
                com.sun.tools.javac.util.List.from(newParamTypes),
                newReturnType,
                com.sun.tools.javac.util.List.from(newThrownTypes),
                instantiatedMethod.tsym
        );

        // Copy annotations
        if (genericType.getMetadata(TypeMetadata.Annotations.class) != null) {
            result = (Type.MethodType) result.addMetadata(genericType.getMetadata(TypeMetadata.Annotations.class));
        }

        return result;
    }

    @Override
    public Type visitClassType(Type.ClassType genericType, Type instantiatedType) {
        if (!(instantiatedType instanceof Type.ClassType)) {
            return instantiatedType;
        }

        Type.ClassType instantiatedClass = (Type.ClassType) instantiatedType;
        Type result = instantiatedClass;

        // Copy annotations from generic to instantiated
        if (genericType.getMetadata(TypeMetadata.Annotations.class) != null) {
            result = result.addMetadata(genericType.getMetadata(TypeMetadata.Annotations.class));
        }

        // Recursively handle type arguments
        if (genericType.getTypeArguments().nonEmpty() &&
                instantiatedClass.getTypeArguments().nonEmpty()) {

            List<Type> newTypeArgs = new ArrayList<>();
            for (int i = 0; i < Math.min(genericType.getTypeArguments().size(),
                    instantiatedClass.getTypeArguments().size()); i++) {
                Type genericArg = genericType.getTypeArguments().get(i);
                Type instantiatedArg = instantiatedClass.getTypeArguments().get(i);
                newTypeArgs.add(visit(genericArg, instantiatedArg));
            }

            // Create new ClassType with updated type arguments
            result = new Type.ClassType(result.getEnclosingType(),
                    com.sun.tools.javac.util.List.from(newTypeArgs),
                    result.tsym,
                    result.getMetadata());
        }

        return result;
    }

    @Override
    public Type visitArrayType(Type.ArrayType genericType, Type instantiatedType) {
        if (!(instantiatedType instanceof Type.ArrayType)) {
            return instantiatedType;
        }

        Type.ArrayType instantiatedArray = (Type.ArrayType) instantiatedType;
        Type newElementType = visit(genericType.elemtype, instantiatedArray.elemtype);

        Type result = new Type.ArrayType(newElementType, instantiatedArray.tsym);

        // Copy annotations
        if (genericType.getMetadata(TypeMetadata.Annotations.class) != null) {
            result = result.addMetadata(genericType.getMetadata(TypeMetadata.Annotations.class));
        }

        return result;
    }

    @Override
    public Type visitTypeVar(Type.TypeVar genericType, Type instantiatedType) {
        // Type variable should be replaced by instantiated type
        // but we still want to preserve any annotations on the type variable
        if (genericType.getMetadata(TypeMetadata.Annotations.class) != null) {
            return instantiatedType.addMetadata(genericType.getMetadata(TypeMetadata.Annotations.class));
        }
        return instantiatedType;
    }

    @Override
    public Type visitType(Type genericType, Type instantiatedType) {
        // Default case - just return instantiated type with any metadata from generic
        if (genericType.getMetadata(TypeMetadata.Annotations.class) != null) {
            return instantiatedType.addMetadata(genericType.getMetadata(TypeMetadata.Annotations.class));
        }
        return instantiatedType;
    }
}

