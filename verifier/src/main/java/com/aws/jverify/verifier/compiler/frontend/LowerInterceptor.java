package com.aws.jverify.verifier.compiler.frontend;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.Lower;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;

import java.lang.reflect.Field;
import java.util.concurrent.Callable;

public class LowerInterceptor {
    public static JCTree.JCExpression intercept(@SuperCall Callable<JCTree.JCExpression> original,
                                                @This Object lowerInstance,
                                                @Argument(0) Symbol sym,
                                                @Argument(1) JCTree.JCExpression tree,
                                                @Argument(2) JCTree.JCExpression enclOp,
                                                @Argument(3) boolean refSuper,
                                                @AllArguments Object[] args) throws Exception {
        JCTree.JCExpression result = original.call();

        if (sym.kind == Kinds.Kind.TYP && tree instanceof JCTree.JCTypeApply typeApply) {

            Field makeField = lowerInstance.getClass().getDeclaredField("make");
            //makeField.setAccessible(true);
            TreeMaker make = (TreeMaker) makeField.get(lowerInstance);
            
            make.pos = tree.pos;

            JCTree.JCTypeApply newTypeApply = make.TypeApply(result, typeApply.arguments);
            newTypeApply.type = result.type;
            result = newTypeApply;
        }

        return result;
    }
}
