package com.aws.jverify.verifier.compiler.simplifications;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeMetadata;
import com.sun.tools.javac.code.Types;

import java.util.List;

class InplaceParallelTypeVisitor extends Types.SimpleVisitor<Void, Type> {

    @Override
    public Void visitClassType(Type.ClassType genericType, Type instantiatedType) {
        if (!(instantiatedType instanceof Type.ClassType)) {
            return null;
        }

        Type.ClassType instantiatedClass = (Type.ClassType) instantiatedType;

        // Copy annotations from generic to instantiated (destructive update)
        if (genericType.getMetadata(TypeMetadata.Annotations.class) != null) {
            instantiatedClass.addMetadata(genericType.getMetadata(TypeMetadata.Annotations.class));
        }

        // Recursively handle type arguments
        if (genericType.getTypeArguments().nonEmpty() &&
                instantiatedClass.getTypeArguments().nonEmpty()) {

            for (int i = 0; i < Math.min(genericType.getTypeArguments().size(),
                    instantiatedClass.getTypeArguments().size()); i++) {
                Type genericArg = genericType.getTypeArguments().get(i);
                Type instantiatedArg = instantiatedClass.getTypeArguments().get(i);
                visit(genericArg, instantiatedArg);
            }
        }

        return null;
    }

    @Override
    public Void visitMethodType(Type.MethodType genericType, Type instantiatedType) {
        if (!(instantiatedType instanceof Type.MethodType)) {
            return null;
        }

        Type.MethodType instantiatedMethod = (Type.MethodType) instantiatedType;

        // Copy annotations (destructive update)
        if (genericType.getMetadata(TypeMetadata.Annotations.class) != null) {
            instantiatedMethod.addMetadata(genericType.getMetadata(TypeMetadata.Annotations.class));
        }

        // Process return type
        visit(genericType.getReturnType(), instantiatedMethod.getReturnType());

        // Process parameter types
        List<Type> genericParams = genericType.getParameterTypes();
        List<Type> instantiatedParams = instantiatedMethod.getParameterTypes();

        for (int i = 0; i < Math.min(genericParams.size(), instantiatedParams.size()); i++) {
            visit(genericParams.get(i), instantiatedParams.get(i));
        }

        // Process thrown types
        List<Type> genericThrown = genericType.getThrownTypes();
        List<Type> instantiatedThrown = instantiatedMethod.getThrownTypes();

        for (int i = 0; i < Math.min(genericThrown.size(), instantiatedThrown.size()); i++) {
            visit(genericThrown.get(i), instantiatedThrown.get(i));
        }

        return null;
    }

    @Override
    public Void visitArrayType(Type.ArrayType genericType, Type instantiatedType) {
        if (!(instantiatedType instanceof Type.ArrayType)) {
            return null;
        }

        Type.ArrayType instantiatedArray = (Type.ArrayType) instantiatedType;

        // Copy annotations (destructive update)
        if (genericType.getMetadata(TypeMetadata.Annotations.class) != null) {
            instantiatedArray.addMetadata(genericType.getMetadata(TypeMetadata.Annotations.class));
        }

        // Process element type
        visit(genericType.elemtype, instantiatedArray.elemtype);

        return null;
    }

    @Override
    public Void visitTypeVar(Type.TypeVar genericType, Type instantiatedType) {
        // Type variable should be replaced by instantiated type
        // but we still want to preserve any annotations on the type variable
        if (genericType.getMetadata(TypeMetadata.Annotations.class) != null) {
            instantiatedType.addMetadata(genericType.getMetadata(TypeMetadata.Annotations.class));
        }
        return null;
    }

    @Override
    public Void visitType(Type genericType, Type instantiatedType) {
        // Default case - copy annotations to instantiated type
        if (genericType.getMetadata(TypeMetadata.Annotations.class) != null) {
            instantiatedType.addMetadata(genericType.getMetadata(TypeMetadata.Annotations.class));
        }
        return null;
    }
}
