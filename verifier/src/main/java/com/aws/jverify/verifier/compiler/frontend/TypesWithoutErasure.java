package com.aws.jverify.verifier.compiler.frontend;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.util.Context;

public class TypesWithoutErasure extends Types {

    public static void preRegister(Context context) {
        context.put(typesKey, new Context.Factory<Types>() {
            public Types make(Context c) {
                return new TypesWithoutErasure(c);
            }
        });
    }
    
    public TypesWithoutErasure(Context context) {
        super(context);
    }

    @Override
    public Type erasure(Type t) {
        return t;
    }
}
