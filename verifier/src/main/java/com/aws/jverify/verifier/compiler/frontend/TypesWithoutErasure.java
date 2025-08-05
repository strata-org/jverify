package com.aws.jverify.verifier.compiler.frontend;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.util.Context;
import java.util.function.Supplier;

public class TypesWithoutErasure extends Types {

    public boolean eraseTypes = true;
    
    public <T> T withErased(Supplier<T> supplier) {
        var previous = eraseTypes;
        eraseTypes = false;
        var result = supplier.get();
        eraseTypes = previous;
        return result;
    }
    
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
        if (eraseTypes) {
            return super.erasure(t);
        }
        return t;
    }
}
