package com.aws.jverify.verifier.compiler;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Pair;

import java.util.HashMap;
import java.util.Map;

public class JVerifyIndex extends JCTree.Visitor {

    private final Map<Symbol, Env<AttrContext>> envs = new HashMap<>();

    /** Visitor argument: the current environment.
     */
    protected Env<AttrContext> currentEnv;

    private final Enter enter;

    public static JVerifyIndex instance(Context context) {
        JVerifyIndex instance = context.get(JVerifyIndex.class);
        if (instance == null)
            instance = new JVerifyIndex(context);
        return instance;
    }

    protected JVerifyIndex(Context context) {
        context.put(JVerifyIndex.class, this);

        enter = Enter.instance(context);
    }

    void index(Env<AttrContext> env, JCTree tree) {
        this.currentEnv = env;
        tree.accept(this);
    }

    void index(Env<AttrContext> env, List<? extends JCTree> trees) {
        this.currentEnv = env;
        trees.forEach(tree -> tree.accept(this));
    }

    /** Visitor method: enter classes of a list of trees.
     */
    <T extends JCTree> void classEnter(List<T> trees) {
        ListBuffer<Type> ts = new ListBuffer<>();
        for (List<T> l = trees; l.nonEmpty(); l = l.tail) {
            l.head.accept(this);
        }
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl that) {
        envs.put(that.sym, enter.classEnv(that, currentEnv));

        classEnter(that.defs);
    }

    @Override
    public void visitTree(JCTree tree) {
    }

    public Env<AttrContext> getEnv(Symbol sym) {
        return envs.get(sym);
    }

    public Pair<JCTree, JCTree.JCCompilationUnit> getTreeAndTopLevel(Symbol sym) {
        Env<AttrContext> enterEnv = getEnv(sym);
        if (enterEnv == null)
            return null;
        JCTree tree = TreeInfo.declarationFor(sym, enterEnv.tree);
        if (tree == null || enterEnv.toplevel == null)
            return null;
        return new Pair<>(tree, enterEnv.toplevel);
    }

    public JCTree getTree(Symbol sym) {
        Env<AttrContext> enterEnv = getEnv(sym);
        if (enterEnv == null)
            return null;
        Pair<JCTree, JCTree.JCCompilationUnit> treeTopLevel = getTreeAndTopLevel(sym);
        if (treeTopLevel == null)
            return null;
        return treeTopLevel.fst;
    }
}
