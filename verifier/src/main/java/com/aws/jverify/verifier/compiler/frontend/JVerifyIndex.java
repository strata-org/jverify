package com.aws.jverify.verifier.compiler.frontend;

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

/**
 * A replacement for some of the lookup functionality of
 * indexes like JavacTrees and Enter.
 * <p>
 * This is helpful because the LOWER javac phase
 * consumes and clears some of the state those classes depend on,
 * such as the map inside TypeEnvs.
 * <p>
 * We could conceivably re-populate it again instead,
 * but there's a risk of incorrect behavior when reapplying phases
 * to the same AST nodes.
 * Having our own type helps decouple us from the particulars
 * of the javac pipeline somewhat.
 */
public class JVerifyIndex extends JCTree.Visitor {

    private final Map<Symbol, Env<AttrContext>> envs = new HashMap<>();

    /** Visitor argument: the current environment.
     */
    protected Env<AttrContext> currentEnv;

    private final Enter enter;

    public static JVerifyIndex instance(Context context) {
        JVerifyIndex instance = context.get(JVerifyIndex.class);
        if (instance == null) {
            instance = new JVerifyIndex(context);
        }
        return instance;
    }
    
    public void put(Symbol symbol, Env<AttrContext> env) {
        envs.put(symbol, env);
    }

    protected JVerifyIndex(Context context) {
        context.put(JVerifyIndex.class, this);

        enter = Enter.instance(context);
    }

    public void index(Env<AttrContext> env, JCTree tree) {
        this.currentEnv = env;
        tree.accept(this);
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
        // Do nothing
    }

    public Env<AttrContext> getEnv(Symbol sym) {
        if (sym == null) {
            return null;
        }

        // Get enclosing class of sym, or sym itself if it is a class
        // package, or module.
        // (Same logic as JavacElements.getEnterEnv())
        Symbol.TypeSymbol ts = switch (sym.kind) {
            case PCK -> (Symbol.PackageSymbol)sym;
            case MDL -> (Symbol.ModuleSymbol)sym;
            default -> sym.enclClass();
        };
        return envs.get(ts);
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
