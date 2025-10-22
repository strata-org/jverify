package com.aws.jverify.verifier.compiler.frontend;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.util.Context;
import java.util.function.Supplier;

public class TypesWithoutErasure extends Types {

    public boolean eraseTypes = true;
    
    public static void preRegister(Context context) {
        context.put(typesKey, (Context.Factory<Types>) TypesWithoutErasure::new);
    }
    
    public TypesWithoutErasure(Context context) {
        super(context);
    }

    @Override
    public boolean isSameType(Type t, Type s) {
        var previous = eraseTypes;
        eraseTypes = false;
        var result = super.isSameType(t, s);
        eraseTypes = previous;
        return result;
    }

    @Override
    public Type erasure(Type t) {
        if (eraseTypes) {
            return super.erasure(t);
        }
        return t;
    }
}
