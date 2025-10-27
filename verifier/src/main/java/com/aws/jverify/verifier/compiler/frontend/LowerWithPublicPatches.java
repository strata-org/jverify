package com.aws.jverify.verifier.compiler.frontend;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.*;

import java.lang.reflect.Field;

import static com.sun.tools.javac.code.Flags.SYNTHETIC;

public class LowerWithPublicPatches extends Lower  {
    private final TreeMaker make;
    private final Names names;
    private final Symtab syms;
    private final Types types;
    private final Resolve rs;
    private final TransTypes transTypes;
    static Field typesField;
    
    private final Target target;

    static Field attrEnvField;
    static Field currentMethodSymField;

    static {
        try {
            attrEnvField = Lower.class.getDeclaredField("attrEnv");
            currentMethodSymField = Lower.class.getDeclaredField("currentMethodSym");
            typesField = Lower.class.getDeclaredField("types");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    
    protected LowerWithPublicPatches(Context context) {
        super(context);
        make = TreeMaker.instance(context);
        names = Names.instance(context);
        syms = Symtab.instance(context);
        types = Types.instance(context);
        rs = Resolve.instance(context);
        transTypes = TransTypes.instance(context);
        target = Target.instance(context);
    }
    
    public static LowerWithPublicPatches instance(Context context) {
        LowerWithPublicPatches instance = (LowerWithPublicPatches)context.get(lowerKey);
        if (instance == null)
            instance = new LowerWithPublicPatches(context);
        return instance;
    }

    @Override
    public void visitForeachLoop(JCTree.JCEnhancedForLoop tree) {
        //super.visitForeachLoop(tree);
        visitForEachLoopModified(tree);
    }

    void visitForEachLoopModified(JCTree.JCEnhancedForLoop tree) {
        try {
            TypesWithoutErasure types = (TypesWithoutErasure) typesField.get(this);
            var previous = types.eraseTypes;
            types.eraseTypes = false;
            
            if (types.elemtype(tree.expr.type) == null) {
                visitIterableForeachLoopModified(tree);
                var typedResult = (JCTree.JCForLoop)result;
                var blockBody = (JCTree.JCBlock)typedResult.body;
                var added1 = blockBody.stats.get(0);
                var added2 = blockBody.stats.get(1);
                var original = (JCTree.JCBlock)blockBody.stats.get(2);
                var contract = (JCTree.JCBlock)original.stats.get(0);
                contract.stats = contract.stats.prepend(added2);
                var implementation = original.stats.get(1);
                original.stats = List.of(contract, make.
                        Block(0, List.of(added1, implementation)));
                typedResult.body = original;
            }
            else {
                super.visitForeachLoop(tree);
                var typedResult = (JCTree.JCForLoop)result;
                var blockBody = (JCTree.JCBlock)typedResult.body;
                var added1 = blockBody.stats.get(0);
                var original = (JCTree.JCBlock)blockBody.stats.get(1);
                var contract = (JCTree.JCBlock)original.stats.get(0);
                var implementation = original.stats.get(1);
                original.stats = List.of(contract, make.
                        Block(0, List.of(added1, implementation)));
                typedResult.body = original;
            }
            types.eraseTypes = previous;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void visitIterableForeachLoopModified(JCTree.JCEnhancedForLoop tree) throws IllegalAccessException {
        var currentMethodSym = (Symbol.MethodSymbol) currentMethodSymField.get(this);
        
        make_at(tree.expr.pos());
        Type iterableType = types.asSuper(types.cvarUpperBound(tree.expr.type),
                syms.iterableType.tsym);
        Type iteratorTarget = syms.objectType;
        if (iterableType.getTypeArguments().nonEmpty()) {
            iteratorTarget = types.erasure(iterableType.getTypeArguments().head);
        }
// Remove erasing calls
//        tree.expr.type = types.erasure(types.skipTypeVars(tree.expr.type, false));
//        tree.expr = transTypes.coerce(attrEnv, tree.expr, types.erasure(iterableType));
        Symbol iterator = lookupMethod(tree.expr.pos(),
                names.iterator,
                tree.expr.type,
                List.nil());
        
        // Added
        Type iteratorType = types.subst(syms.iteratorType, syms.iteratorType.getTypeArguments(),
                 iterableType.getTypeArguments());
        
        // Removed because this fails
        // Assert.check(types.isSameType(types.erasure(types.asSuper(iterator.type.getReturnType(), syms.iteratorType.tsym)), types.erasure(syms.iteratorType)));
        Symbol.VarSymbol itvar = new Symbol.VarSymbol(SYNTHETIC, names.fromString("i" + target.syntheticNameChar()),
                types.erasure(iteratorType /* syms.iteratorType */),
                currentMethodSym);

        JCTree.JCStatement init = make.
                VarDef(itvar, make.App(make.Select(tree.expr, iterator)
                        .setType(types.erasure(iterator.type))));

        var cond = getCondition(tree, itvar);
        var indexDef = getElementVarDeclaration(tree, iteratorType, itvar, iteratorTarget);
        var decreasesCallStatement = getDecreasesCallStatement(itvar);

        JCTree.JCBlock body = make.Block(0, List.of(indexDef, decreasesCallStatement, tree.body));
        body.endpos = TreeInfo.endPos(tree.body);
        result = translate(make.
                ForLoop(List.of(init),
                        cond,
                        List.nil(),
                        body));
        patchTargets(body, tree, result);
    }

    private JCTree.JCMethodInvocation getCondition(JCTree.JCEnhancedForLoop tree, Symbol.VarSymbol itvar) throws IllegalAccessException {
        Symbol hasNext = lookupMethod(tree.expr.pos(),
                names.hasNext,
                syms.iteratorType /* itvar.type */,
                List.nil());
        return make.App(make.Select(make.Ident(itvar), hasNext));
    }

    private JCTree.JCVariableDecl getElementVarDeclaration(JCTree.JCEnhancedForLoop tree, Type iteratorType, Symbol.VarSymbol itvar, Type iteratorTarget) throws IllegalAccessException {
        Symbol next = lookupMethod(tree.expr.pos(),
                names.next,
                iteratorType /* itvar.type */,
                List.nil());
        JCTree.JCExpression vardefinit = make.App(make.Select(make.Ident(itvar), next));
        vardefinit.type = iteratorType.getTypeArguments().getFirst();
        if (tree.var.type.isPrimitive())
            vardefinit = make.TypeCast(types.cvarUpperBound(iteratorTarget), vardefinit);
        else
            vardefinit = make.TypeCast(tree.var.type, vardefinit);

        JCTree.JCVariableDecl indexDef = (JCTree.JCVariableDecl)make.VarDef(tree.var.mods,
                tree.var.name,
                tree.var.vartype,
                vardefinit).setType(tree.var.type);
        indexDef.sym = tree.var.sym;
        return indexDef;
    }

    private JCTree.JCExpressionStatement getDecreasesCallStatement(Symbol.VarSymbol itvar) {
        var jverifyClasses = syms.getClassesForName(names.fromString("com.aws.jverify.JVerify"));
        Symbol.ClassSymbol jverifyClass = jverifyClasses.iterator().next();

        Symbol.MethodSymbol decreasesMethod = null;
        for (Symbol sym : jverifyClass.members().getSymbolsByName(names.fromString("decreases"))) {
            if (sym instanceof Symbol.MethodSymbol methodSymbol && methodSymbol.getParameters().size() == 1) {
                decreasesMethod = methodSymbol;
                break;
            }
        }

        JCTree.JCFieldAccess methodSelect = make.Select(
                make.QualIdent(jverifyClass),
                decreasesMethod
        );
        methodSelect.type = decreasesMethod.type;
        methodSelect.sym = decreasesMethod;

        JCTree.JCIdent iArg = make.Ident(itvar); // iSymbol should be the resolved symbol for 'i'
        iArg.type = itvar.type;
        iArg.sym = itvar;

        var iteratorClasses = syms.getClassesForName(names.fromString("com.aws.jverify.builtin.IteratorContract"));
        Symbol.ClassSymbol iteratorClass = iteratorClasses.iterator().next();

        Symbol.VarSymbol remainingEntries = null;
        for (Symbol sym : iteratorClass.members().getSymbolsByName(names.fromString("remainingEntries"))) {
            if (sym instanceof Symbol.VarSymbol varSymbol) {
                remainingEntries = varSymbol;
                break;
            }
        }

        JCTree.JCMethodInvocation decreasesCall = make.Apply(
                List.nil(),
                methodSelect,
                List.of(make.Select(iArg, remainingEntries))
        );
        decreasesCall.type = decreasesMethod.getReturnType();
        return make.Exec(decreasesCall);
    }

    /** Look up a method in a given scope.
     */
    private Symbol.MethodSymbol lookupMethod(JCDiagnostic.DiagnosticPosition pos, Name name, Type qual, List<Type> args) throws IllegalAccessException {
        var attrEnv = (Env<AttrContext>) attrEnvField.get(this);
        return rs.resolveInternalMethod(pos, attrEnv, qual, name, args, List.nil());
    }

    /** Patch up break and continue targets. */
    private void patchTargets(JCTree body, final JCTree src, final JCTree dest) {
        class Patcher extends TreeScanner {
            public void visitBreak(JCTree.JCBreak tree) {
                if (tree.target == src)
                    tree.target = dest;
            }
            public void visitYield(JCTree.JCYield tree) {
                if (tree.target == src)
                    tree.target = dest;
                scan(tree.value);
            }
            public void visitContinue(JCTree.JCContinue tree) {
                if (tree.target == src)
                    tree.target = dest;
            }
            public void visitClassDef(JCTree.JCClassDecl tree) {}
        }
        new Patcher().scan(body);
    }
    
    /** Equivalent to make.at(pos.getStartPosition()) with side effect of caching
     *  pos as make_pos, for use in diagnostics.
     **/
    TreeMaker make_at(JCDiagnostic.DiagnosticPosition pos) {
        // make_pos = pos;
        return make.at(pos);
    }
}
