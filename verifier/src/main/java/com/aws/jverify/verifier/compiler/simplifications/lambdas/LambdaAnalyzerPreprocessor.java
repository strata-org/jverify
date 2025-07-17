package com.aws.jverify.verifier.compiler.simplifications.lambdas;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.List;

import java.util.*;

import static com.aws.jverify.verifier.compiler.simplifications.lambdas.LambdaCompiler.LambdaSymbolKind.*;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.Kind.*;
import static com.sun.tools.javac.code.TypeTag.NONE;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

/**
 * Compared to the original LambdaToMethod.LambdaAnalyzerPreprocessor,
 * visitReference has been modified
 * LambdaTranslationContext.translate has been modified
 * 
 * This visitor collects information about translation of a lambda expression.
 * More specifically, it keeps track of the enclosing contexts and captured locals
 * accessed by the lambda being translated (as well as other useful info).
 * It also translates away problems for LambdaToMethod.
 */
class LambdaAnalyzerPreprocessor extends TreeTranslator {

    public final LambdaCompiler lambdaCompiler;
    /**
     * the frame stack - used to reconstruct translation info about enclosing scopes
     */
    public List<Frame> frameStack;

    /**
     * keep the count of lambda expression (used to generate unambiguous
     * names)
     */
    public int lambdaCount = 0;

    /**
     * List of types undergoing construction via explicit constructor chaining.
     */
    public List<Symbol.ClassSymbol> typesUnderConstruction;

    public LambdaAnalyzerPreprocessor(LambdaCompiler lambdaCompiler) {
        this.lambdaCompiler = lambdaCompiler;
    }

    /**
     * keep the count of lambda expression defined in given context (used to
     * generate unambiguous names for serializable lambdas)
     */
    private class SyntheticMethodNameCounter {
        private Map<String, Integer> map = new HashMap<>();

        int getIndex(StringBuilder buf) {
            String temp = buf.toString();
            Integer count = map.get(temp);
            if (count == null) {
                count = 0;
            }
            ++count;
            map.put(temp, count);
            return count;
        }
    }

    private SyntheticMethodNameCounter syntheticMethodNameCounts =
            new SyntheticMethodNameCounter();

    private Map<Symbol, JCTree.JCClassDecl> localClassDefs;

    /**
     * maps for fake clinit symbols to be used as owners of lambda occurring in
     * a static var init context
     */
    private Map<Symbol.ClassSymbol, Symbol> clinits = new HashMap<>();

    public JCTree.JCClassDecl analyzeAndPreprocessClass(JCTree.JCClassDecl tree) {
        frameStack = List.nil();
        typesUnderConstruction = List.nil();
        localClassDefs = new HashMap<>();
        return translate(tree);
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
        List<Symbol.ClassSymbol> previousNascentTypes = typesUnderConstruction;
        try {
            Name methName = TreeInfo.name(tree.meth);
            if (methName == lambdaCompiler.names._this || methName == lambdaCompiler.names._super) {
                typesUnderConstruction = typesUnderConstruction.prepend(currentClass());
            }
            super.visitApply(tree);
        } finally {
            typesUnderConstruction = previousNascentTypes;
        }
    }

    // where
    public Symbol.ClassSymbol currentClass() {
        for (Frame frame : frameStack) {
            if (frame.tree.hasTag(JCTree.Tag.CLASSDEF)) {
                JCTree.JCClassDecl cdef = (JCTree.JCClassDecl) frame.tree;
                return cdef.sym;
            }
        }
        return null;
    }

    @Override
    public void visitBlock(JCTree.JCBlock tree) {
        List<Frame> prevStack = frameStack;
        try {
            if (frameStack.nonEmpty() && frameStack.head.tree.hasTag(CLASSDEF)) {
                frameStack = frameStack.prepend(new Frame(tree));
            }
            super.visitBlock(tree);
        } finally {
            frameStack = prevStack;
        }
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl tree) {
        List<Frame> prevStack = frameStack;
        int prevLambdaCount = lambdaCount;
        SyntheticMethodNameCounter prevSyntheticMethodNameCounts =
                syntheticMethodNameCounts;
        Map<Symbol.ClassSymbol, Symbol> prevClinits = clinits;
        DiagnosticSource prevSource = lambdaCompiler.log.currentSource();
        try {
            lambdaCompiler.log.useSource(tree.sym.sourcefile);
            lambdaCount = 0;
            syntheticMethodNameCounts = new SyntheticMethodNameCounter();
            prevClinits = new HashMap<>();
            if (tree.sym.owner.kind == MTH) {
                localClassDefs.put(tree.sym, tree);
            }
            if (directlyEnclosingLambda() != null) {
                tree.sym.owner = owner();
                if (tree.sym.hasOuterInstance()) {
                    //if a class is defined within a lambda, the lambda must capture
                    //its enclosing instance (if any)
                    TranslationContext<?> localContext = context();
                    final Symbol.TypeSymbol outerInstanceSymbol = tree.sym.type.getEnclosingType().tsym;
                    while (localContext != null && !localContext.owner.isStatic()) {
                        if (localContext.tree.hasTag(LAMBDA)) {
                            JCTree block = capturedDecl(localContext.depth, outerInstanceSymbol);
                            if (block == null) break;
                            ((LambdaTranslationContext) localContext)
                                    .addSymbol(outerInstanceSymbol, CAPTURED_THIS);
                        }
                        localContext = localContext.prev;
                    }
                }
            }
            frameStack = frameStack.prepend(new Frame(tree));
            super.visitClassDef(tree);
        } finally {
            lambdaCompiler.log.useSource(prevSource.getFile());
            frameStack = prevStack;
            lambdaCount = prevLambdaCount;
            syntheticMethodNameCounts = prevSyntheticMethodNameCounts;
            clinits = prevClinits;
        }
    }

    @Override
    public void visitIdent(JCTree.JCIdent tree) {
        if (context() != null && lambdaIdentSymbolFilter(tree.sym)) {
            if (tree.sym.kind == VAR &&
                    tree.sym.owner.kind == MTH &&
                    tree.type.constValue() == null) {
                TranslationContext<?> localContext = context();
                while (localContext != null) {
                    if (localContext.tree.getTag() == LAMBDA) {
                        JCTree block = capturedDecl(localContext.depth, tree.sym);
                        if (block == null) break;
                        ((LambdaTranslationContext) localContext)
                                .addSymbol(tree.sym, CAPTURED_VAR);
                    }
                    localContext = localContext.prev;
                }
            } else if (tree.sym.owner.kind == TYP) {
                TranslationContext<?> localContext = context();
                while (localContext != null && !localContext.owner.isStatic()) {
                    if (localContext.tree.hasTag(LAMBDA)) {
                        JCTree block = capturedDecl(localContext.depth, tree.sym);
                        if (block == null) break;
                        switch (block.getTag()) {
                            case CLASSDEF:
                                ((LambdaTranslationContext) localContext)
                                        .addSymbol(tree.sym, CAPTURED_THIS);
                                break;
                            default:
                                Assert.error("bad block kind");
                        }
                    }
                    localContext = localContext.prev;
                }
            }
        }
        super.visitIdent(tree);
    }

    @Override
    public void visitLambda(JCTree.JCLambda tree) {
        analyzeLambda(tree, "lambda.stat");
    }

    private void analyzeLambda(JCTree.JCLambda tree, JCTree.JCExpression methodReferenceReceiver) {
        // Translation of the receiver expression must occur first
        JCTree.JCExpression rcvr = translate(methodReferenceReceiver);
        LambdaTranslationContext context = analyzeLambda(tree, "mref.stat.1");
        if (rcvr != null) {
            context.methodReferenceReceiver = rcvr;
        }
    }

    private LambdaTranslationContext analyzeLambda(JCTree.JCLambda tree, String statKey) {
        List<Frame> prevStack = frameStack;
        try {
            LambdaTranslationContext context = new LambdaTranslationContext(this, tree);
            frameStack = frameStack.prepend(new Frame(tree));
            for (JCTree.JCVariableDecl param : tree.params) {
                context.addSymbol(param.sym, PARAM);
                frameStack.head.addLocal(param.sym);
            }
            lambdaCompiler.contextMap.put(tree, context);
            super.visitLambda(tree);
            context.complete();
            if (lambdaCompiler.dumpLambdaToMethodStats) {
                lambdaCompiler.log.note(tree, lambdaCompiler.diags.noteKey(statKey, context.needsAltMetafactory(), context.translatedSym));
            }
            return context;
        } finally {
            frameStack = prevStack;
        }
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        List<Frame> prevStack = frameStack;
        try {
            frameStack = frameStack.prepend(new Frame(tree));
            super.visitMethodDef(tree);
        } finally {
            frameStack = prevStack;
        }
    }

    @Override
    public void visitNewClass(JCTree.JCNewClass tree) {
        Symbol.TypeSymbol def = tree.type.tsym;
        boolean inReferencedClass = currentlyInClass(def);
        boolean isLocal = def.isDirectlyOrIndirectlyLocal();
        if ((inReferencedClass && isLocal || lambdaNewClassFilter(context(), tree))) {
            TranslationContext<?> localContext = context();
            final Symbol.TypeSymbol outerInstanceSymbol = tree.type.getEnclosingType().tsym;
            while (localContext != null && !localContext.owner.isStatic()) {
                if (localContext.tree.hasTag(LAMBDA)) {
                    if (outerInstanceSymbol != null) {
                        JCTree block = capturedDecl(localContext.depth, outerInstanceSymbol);
                        if (block == null) break;
                    }
                    ((LambdaTranslationContext) localContext)
                            .addSymbol(outerInstanceSymbol, CAPTURED_THIS);
                }
                localContext = localContext.prev;
            }
        }
        super.visitNewClass(tree);
        if (context() != null && !inReferencedClass && isLocal) {
            LambdaTranslationContext lambdaContext = (LambdaTranslationContext) context();
            captureLocalClassDefs(def, lambdaContext);
        }
    }

    //where
    void captureLocalClassDefs(Symbol csym, final LambdaTranslationContext lambdaContext) {
            JCTree.JCClassDecl localCDef = localClassDefs.get(csym);
            if (localCDef != null && lambdaContext.freeVarProcessedLocalClasses.add(csym)) {
                BasicFreeVarCollector fvc = new BasicFreeVarCollector(lambdaCompiler) {
                    @Override
                    void addFreeVars(Symbol.ClassSymbol c) {
                        captureLocalClassDefs(c, lambdaContext);
                    }
                    @Override
                    void visitSymbol(Symbol sym) {
                        if (sym.kind == VAR &&
                                sym.owner.kind == MTH &&
                                ((Symbol.VarSymbol)sym).getConstValue() == null) {
                            TranslationContext<?> localContext = context();
                            while (localContext != null) {
                                if (localContext.tree.getTag() == LAMBDA) {
                                    JCTree block = capturedDecl(localContext.depth, sym);
                                    if (block == null) break;
                                    ((LambdaTranslationContext)localContext).addSymbol(sym, CAPTURED_VAR);
                                }
                                localContext = localContext.prev;
                            }
                        }
                    }
                };
                fvc.scan(localCDef);
            }
    }

    //where
    boolean currentlyInClass(Symbol csym) {
        for (Frame frame : frameStack) {
            if (frame.tree.hasTag(JCTree.Tag.CLASSDEF)) {
                JCTree.JCClassDecl cdef = (JCTree.JCClassDecl) frame.tree;
                if (cdef.sym == csym) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Method references to local class constructors, may, if the local
     * class references local variables, have implicit constructor
     * parameters added in Lower; As a result, the invokedynamic bootstrap
     * information added in the LambdaToMethod pass will have the wrong
     * signature. Hooks between Lower and LambdaToMethod have been added to
     * handle normal "new" in this case. This visitor converts potentially
     * affected method references into a lambda containing a normal
     * expression.
     *
     * @param tree
     */
    @Override
    public void visitReference(JCTree.JCMemberReference tree) {
        ReferenceTranslationContext rcontext = new ReferenceTranslationContext(this, tree);
        lambdaCompiler.contextMap.put(tree, rcontext);
        MemberReferenceToLambda conv = new MemberReferenceToLambda(this.lambdaCompiler, tree, rcontext, owner());
        JCTree.JCLambda lambda = conv.lambda();
        analyzeLambda(lambda, conv.getReceiverExpression());
    }

    @Override
    public void visitSelect(JCTree.JCFieldAccess tree) {
        if (context() != null && tree.sym.kind == VAR &&
                (tree.sym.name == lambdaCompiler.names._this ||
                        tree.sym.name == lambdaCompiler.names._super)) {
            // A select of this or super means, if we are in a lambda,
            // we much have an instance context
            TranslationContext<?> localContext = context();
            while (localContext != null && !localContext.owner.isStatic()) {
                if (localContext.tree.hasTag(LAMBDA)) {
                    JCTree.JCClassDecl clazz = (JCTree.JCClassDecl) capturedDecl(localContext.depth, tree.sym);
                    if (clazz == null) break;
                    ((LambdaTranslationContext) localContext).addSymbol(clazz.sym, CAPTURED_THIS);
                }
                localContext = localContext.prev;
            }
        }
        super.visitSelect(tree);
    }

    @Override
    public void visitVarDef(JCTree.JCVariableDecl tree) {
        TranslationContext<?> context = context();
        if (context != null && context instanceof LambdaTranslationContext lambdaContext) {
            for (Frame frame : frameStack) {
                if (frame.tree.hasTag(VARDEF)) {
                    //skip variable frames inside a lambda:
                    continue;
                } else if (frame.tree.hasTag(LAMBDA)) {
                    lambdaContext.addSymbol(tree.sym, LOCAL_VAR);
                } else {
                    break;
                }
            }
            // Check for type variables (including as type arguments).
            // If they occur within class nested in a lambda, mark for erasure
            Type type = tree.sym.asType();
        }

        List<Frame> prevStack = frameStack;
        try {
            if (tree.sym.owner.kind == MTH) {
                frameStack.head.addLocal(tree.sym);
            }
            frameStack = frameStack.prepend(new Frame(tree));
            super.visitVarDef(tree);
        } finally {
            frameStack = prevStack;
        }
    }

    /**
     * Return a valid owner given the current declaration stack
     * (required to skip synthetic lambda symbols)
     */
    private Symbol owner() {
        return owner(false);
    }

    @SuppressWarnings("fallthrough")
    public Symbol owner(boolean skipLambda) {
        List<Frame> frameStack2 = frameStack;
        while (frameStack2.nonEmpty()) {
            switch (frameStack2.head.tree.getTag()) {
                case VARDEF:
                    if (((JCTree.JCVariableDecl) frameStack2.head.tree).sym.isDirectlyOrIndirectlyLocal()) {
                        frameStack2 = frameStack2.tail;
                        break;
                    }
                    JCTree.JCClassDecl cdecl = (JCTree.JCClassDecl) frameStack2.tail.head.tree;
                    return initSym(cdecl.sym,
                            ((JCTree.JCVariableDecl) frameStack2.head.tree).sym.flags() & STATIC);
                case BLOCK:
                    JCTree.JCClassDecl cdecl2 = (JCTree.JCClassDecl) frameStack2.tail.head.tree;
                    return initSym(cdecl2.sym,
                            ((JCTree.JCBlock) frameStack2.head.tree).flags & STATIC);
                case CLASSDEF:
                    return ((JCTree.JCClassDecl) frameStack2.head.tree).sym;
                case METHODDEF:
                    return ((JCTree.JCMethodDecl) frameStack2.head.tree).sym;
                case LAMBDA:
                    if (!skipLambda)
                        return ((LambdaTranslationContext) lambdaCompiler.contextMap
                                .get(frameStack2.head.tree)).translatedSym;
                default:
                    frameStack2 = frameStack2.tail;
            }
        }
        Assert.error();
        return null;
    }

    private Symbol initSym(Symbol.ClassSymbol csym, long flags) {
        boolean isStatic = (flags & STATIC) != 0;
        if (isStatic) {
            /* static clinits are generated in Gen, so we need to use a fake
             * one. Attr creates a fake clinit method while attributing
             * lambda expressions used as initializers of static fields, so
             * let's use that one.
             */
            Symbol.MethodSymbol clinit = lambdaCompiler.attr.removeClinit(csym);
            if (clinit != null) {
                clinits.put(csym, clinit);
                return clinit;
            }

            /* if no clinit is found at Attr, then let's try at clinits.
             */
            clinit = (Symbol.MethodSymbol) clinits.get(csym);
            if (clinit == null) {
                /* no luck, let's create a new one
                 */
                clinit = lambdaCompiler.makePrivateSyntheticMethod(STATIC,
                        lambdaCompiler.names.clinit,
                        new Type.MethodType(List.nil(), lambdaCompiler.syms.voidType,
                                List.nil(), lambdaCompiler.syms.methodClass),
                        csym);
                clinits.put(csym, clinit);
            }
            return clinit;
        } else {
            //get the first constructor and treat it as the instance init sym
            for (Symbol s : csym.members_field.getSymbolsByName(lambdaCompiler.names.init)) {
                return s;
            }
        }
        Assert.error("init not found");
        return null;
    }

    private JCTree directlyEnclosingLambda() {
        if (frameStack.isEmpty()) {
            return null;
        }
        List<Frame> frameStack2 = frameStack;
        while (frameStack2.nonEmpty()) {
            switch (frameStack2.head.tree.getTag()) {
                case CLASSDEF:
                case METHODDEF:
                    return null;
                case LAMBDA:
                    return frameStack2.head.tree;
                default:
                    frameStack2 = frameStack2.tail;
            }
        }
        Assert.error();
        return null;
    }

    private boolean inClassWithinLambda() {
        if (frameStack.isEmpty()) {
            return false;
        }
        List<Frame> frameStack2 = frameStack;
        boolean classFound = false;
        while (frameStack2.nonEmpty()) {
            switch (frameStack2.head.tree.getTag()) {
                case LAMBDA:
                    return classFound;
                case CLASSDEF:
                    classFound = true;
                    frameStack2 = frameStack2.tail;
                    break;
                default:
                    frameStack2 = frameStack2.tail;
            }
        }
        // No lambda
        return false;
    }

    /**
     * Return the declaration corresponding to a symbol in the enclosing
     * scope; the depth parameter is used to filter out symbols defined
     * in nested scopes (which do not need to undergo capture).
     */
    private JCTree capturedDecl(int depth, Symbol sym) {
        int currentDepth = frameStack.size() - 1;
        for (Frame block : frameStack) {
            switch (block.tree.getTag()) {
                case CLASSDEF:
                    Symbol.ClassSymbol clazz = ((JCTree.JCClassDecl) block.tree).sym;
                    if (clazz.isSubClass(sym, lambdaCompiler.types) || sym.isMemberOf(clazz, lambdaCompiler.types)) {
                        return currentDepth > depth ? null : block.tree;
                    }
                    break;
                case VARDEF:
                    if ((((JCTree.JCVariableDecl) block.tree).sym == sym &&
                            sym.owner.kind == MTH) || //only locals are captured
                            (block.locals != null && block.locals.contains(sym))) {
                        return currentDepth > depth ? null : block.tree;
                    }
                    break;
                case BLOCK:
                case METHODDEF:
                case LAMBDA:
                    if (block.locals != null && block.locals.contains(sym)) {
                        return currentDepth > depth ? null : block.tree;
                    }
                    break;
                default:
                    Assert.error("bad decl kind " + block.tree.getTag());
            }
            currentDepth--;
        }
        return null;
    }

    public TranslationContext<?> context() {
        for (Frame frame : frameStack) {
            TranslationContext<?> context = lambdaCompiler.contextMap.get(frame.tree);
            if (context != null) {
                return context;
            }
        }
        return null;
    }

    /**
     * This is used to filter out those identifiers that needs to be adjusted
     * when translating away lambda expressions
     */
    public boolean lambdaIdentSymbolFilter(Symbol sym) {
        return (sym.kind == VAR || sym.kind == MTH)
                && !sym.isStatic()
                && sym.name != lambdaCompiler.names.init;
    }

    /**
     * This is used to filter out those select nodes that need to be adjusted
     * when translating away lambda expressions - at the moment, this is the
     * set of nodes that select `this' (qualified this)
     */
    public boolean lambdaFieldAccessFilter(JCTree.JCFieldAccess fAccess) {
        return (lambdaCompiler.context instanceof LambdaTranslationContext lambdaContext)
                && !fAccess.sym.isStatic()
                && fAccess.name == lambdaCompiler.names._this
                && (fAccess.sym.owner.kind == TYP)
                && !lambdaContext.translatedSymbols.get(CAPTURED_OUTER_THIS).isEmpty();
    }

    /**
     * This is used to filter out those new class expressions that need to
     * be qualified with an enclosing tree
     */
    public boolean lambdaNewClassFilter(TranslationContext<?> context, JCTree.JCNewClass tree) {
        if (context != null
                && tree.encl == null
                && tree.def == null
                && !tree.type.getEnclosingType().hasTag(NONE)) {
            Type encl = tree.type.getEnclosingType();
            Type current = context.owner.enclClass().type;
            while (!current.hasTag(NONE)) {
                if (current.tsym.isSubClass(encl.tsym, lambdaCompiler.types)) {
                    return true;
                }
                current = current.getEnclosingType();
            }
            return false;
        } else {
            return false;
        }
    }

}
