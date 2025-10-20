package com.aws.jverify.verifier.compiler.frontend;

import com.sun.tools.javac.comp.Lower;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

public class LowerWithPublicPatches extends Lower  {
    private final TreeMaker maker;
    
    protected LowerWithPublicPatches(Context context) {
        super(context);
        maker = TreeMaker.instance(context);
    }
    
    public static LowerWithPublicPatches instance(Context context) {
        LowerWithPublicPatches instance = (LowerWithPublicPatches)context.get(lowerKey);
        if (instance == null)
            instance = new LowerWithPublicPatches(context);
        return instance;
    }

    @Override
    public void visitForeachLoop(JCTree.JCEnhancedForLoop tree) {
        super.visitForeachLoop(tree);
        var typedResult = (JCTree.JCForLoop)result;
        var blockBody = (JCTree.JCBlock)typedResult.body;
        var added = blockBody.stats.get(0);
        var original = (JCTree.JCBlock)blockBody.stats.get(1);
        var contract = original.stats.get(0);
        var implementation = original.stats.get(1);
        original.stats = List.of(contract, maker.
                Block(0, List.of(added, implementation)));
        typedResult.body = original;
    }
}
