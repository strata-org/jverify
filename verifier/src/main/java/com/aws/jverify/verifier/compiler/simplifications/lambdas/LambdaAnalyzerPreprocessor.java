package com.aws.jverify.verifier.compiler.simplifications.lambdas;

import com.sun.source.tree.MemberReferenceTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.List;

import javax.lang.model.element.ElementKind;
import java.util.*;

import static com.aws.jverify.verifier.compiler.simplifications.lambdas.LambdaCompiler.LambdaSymbolKind.*;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.Kind.*;
import static com.sun.tools.javac.code.TypeTag.NONE;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

/**
 * Compared to the original LambdaToMethod.LambdaAnalyzerPreprocessor
 * 
 * visitReference has been modified
 * 
 * This visitor collects information about translation of a lambda expression.
 * More specifically, it keeps track of the enclosing contexts and captured locals
 * accessed by the lambda being translated (as well as other useful info).
 * It also translates away problems for LambdaToMethod.
 */
class LambdaAnalyzerPreprocessor extends TreeTranslator {

    private final LambdaCompiler lambdaCompiler;
    /**
     * the frame stack - used to reconstruct translation info about enclosing scopes
     */
    private List<Frame> frameStack;

    /**
     * keep the count of lambda expression (used to generate unambiguous
     * names)
     */
    private int lambdaCount = 0;

    /**
     * List of types undergoing construction via explicit constructor chaining.
     */
    private List<Symbol.ClassSymbol> typesUnderConstruction;

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
    private Symbol.ClassSymbol currentClass() {
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
                                JCTree.JCClassDecl cdecl = (JCTree.JCClassDecl) block;
                                ((LambdaTranslationContext) localContext)
                                        .addSymbol(cdecl.sym, CAPTURED_THIS);
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
            LambdaTranslationContext context = new LambdaTranslationContext(tree);
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
        ReferenceTranslationContext rcontext = new ReferenceTranslationContext(tree);
        lambdaCompiler.contextMap.put(tree, rcontext);
        MemberReferenceToLambda conv = new MemberReferenceToLambda(this.lambdaCompiler, tree, rcontext, owner());
        analyzeLambda(conv.lambda(), conv.getReceiverExpression());
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
    private Symbol owner(boolean skipLambda) {
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

    private TranslationContext<?> context() {
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

    private class Frame {
        final JCTree tree;
        List<Symbol> locals;

        public Frame(JCTree tree) {
            this.tree = tree;
        }

        void addLocal(Symbol sym) {
            if (locals == null) {
                locals = List.nil();
            }
            locals = locals.prepend(sym);
        }
    }

    /**
     * This class is used to store important information regarding translation of
     * lambda expression/method references (see subclasses).
     */
    abstract class TranslationContext<T extends JCTree.JCFunctionalExpression> {

        /**
         * the underlying (untranslated) tree
         */
        final T tree;

        /**
         * points to the adjusted enclosing scope in which this lambda/mref expression occurs
         */
        final Symbol owner;

        /**
         * the depth of this lambda expression in the frame stack
         */
        final int depth;

        /**
         * the enclosing translation context (set for nested lambdas/mref)
         */
        final TranslationContext<?> prev;

        /**
         * list of methods to be bridged by the meta-factory
         */
        final List<Symbol> bridges;

        TranslationContext(T tree) {
            this.tree = tree;
            this.owner = owner(true);
            this.depth = frameStack.size() - 1;
            this.prev = context();
            Symbol.ClassSymbol csym =
                    lambdaCompiler.types.makeFunctionalInterfaceClass(lambdaCompiler.attrEnv, lambdaCompiler.names.empty, tree.target, ABSTRACT | INTERFACE);
            this.bridges = lambdaCompiler.types.functionalInterfaceBridges(csym);
        }

        /**
         * does this functional expression need to be created using alternate metafactory?
         */
        boolean needsAltMetafactory() {
            return tree.target.isIntersection() ||
                    bridges.length() > 1;
        }

        /**
         * @return Name of the enclosing method to be folded into synthetic
         * method name
         */
        String enclosingMethodName() {
            return syntheticMethodNameComponent(owner.name);
        }

        /**
         * @return Method name in a form that can be folded into a
         * component of a synthetic method name
         */
        String syntheticMethodNameComponent(Name name) {
            if (name == null) {
                return "null";
            }
            String methodName = name.toString();
            if (methodName.equals("<clinit>")) {
                methodName = "static";
            } else if (methodName.equals("<init>")) {
                methodName = "new";
            }
            return methodName;
        }
    }

    /**
     * This class retains all the useful information about a lambda expression;
     * the contents of this class are filled by the LambdaAnalyzer visitor,
     * and the used by the main translation routines in order to adjust references
     * to captured locals/members, etc.
     */
    class LambdaTranslationContext extends TranslationContext<JCTree.JCLambda> {

        /**
         * variable in the enclosing context to which this lambda is assigned
         */
        final Symbol self;

        /**
         * variable in the enclosing context to which this lambda is assigned
         */
        final Symbol assignedTo;

        Map<LambdaCompiler.LambdaSymbolKind, Map<Symbol, Symbol>> translatedSymbols;

        /**
         * the synthetic symbol for the method hoisting the translated lambda
         */
        Symbol.MethodSymbol translatedSym;

        List<JCTree.JCVariableDecl> syntheticParams;

        /**
         * to prevent recursion, track local classes processed
         */
        final Set<Symbol> freeVarProcessedLocalClasses;

        /**
         * For method references converted to lambdas.  The method
         * reference receiver expression. Must be treated like a captured
         * variable.
         */
        JCTree.JCExpression methodReferenceReceiver;

        LambdaTranslationContext(JCTree.JCLambda tree) {
            super(tree);
            Frame frame = frameStack.head;
            switch (frame.tree.getTag()) {
                case VARDEF:
                    assignedTo = self = ((JCTree.JCVariableDecl) frame.tree).sym;
                    break;
                case ASSIGN:
                    self = null;
                    assignedTo = TreeInfo.symbol(((JCTree.JCAssign) frame.tree).getVariable());
                    break;
                default:
                    assignedTo = self = null;
                    break;
            }

            // This symbol will be filled-in in complete
            if (owner.kind == MTH) {
                final Symbol.MethodSymbol originalOwner = (Symbol.MethodSymbol) owner.clone(owner.owner);
                this.translatedSym = new Symbol.MethodSymbol(SYNTHETIC | PRIVATE, null, null, owner.enclClass()) {
                    @Override
                    public MethodSymbol originalEnclosingMethod() {
                        return originalOwner;
                    }
                };
            } else {
                this.translatedSym = lambdaCompiler.makePrivateSyntheticMethod(0, null, null, owner.enclClass());
            }
            translatedSymbols = new EnumMap<>(LambdaCompiler.LambdaSymbolKind.class);

            translatedSymbols.put(PARAM, new LinkedHashMap<Symbol, Symbol>());
            translatedSymbols.put(LOCAL_VAR, new LinkedHashMap<Symbol, Symbol>());
            translatedSymbols.put(CAPTURED_VAR, new LinkedHashMap<Symbol, Symbol>());
            translatedSymbols.put(CAPTURED_THIS, new LinkedHashMap<Symbol, Symbol>());
            translatedSymbols.put(CAPTURED_OUTER_THIS, new LinkedHashMap<Symbol, Symbol>());

            freeVarProcessedLocalClasses = new HashSet<>();
        }

        /**
         * For a non-serializable lambda, generate a simple method.
         *
         * @return Name to use for the synthetic lambda method name
         */
        private Name lambdaName() {
            return lambdaCompiler.names.lambda.append(lambdaCompiler.names.fromString(enclosingMethodName() + "$" + lambdaCount++));
        }

        /**
         * Translate a symbol of a given kind into something suitable for the
         * synthetic lambda body
         */
        Symbol translate(final Symbol sym, LambdaCompiler.LambdaSymbolKind skind) {
            Symbol ret;
            switch (skind) {
                case CAPTURED_THIS:
                    ret = sym;  // self represented
                    break;
                case CAPTURED_VAR:
                    ret = new Symbol.VarSymbol(SYNTHETIC | FINAL | PARAMETER, sym.name, sym.type, translatedSym) {
                        @Override
                        public Symbol baseSymbol() {
                            //keep mapping with original captured symbol
                            return sym;
                        }
                    };
                    break;
                case CAPTURED_OUTER_THIS:
                    Name name = lambdaCompiler.names.fromString(sym.flatName().toString().replace('.', '$') + lambdaCompiler.names.dollarThis);
                    ret = new Symbol.VarSymbol(SYNTHETIC | FINAL | PARAMETER, name, sym.type, translatedSym) {
                        @Override
                        public Symbol baseSymbol() {
                            //keep mapping with original captured symbol
                            return sym;
                        }
                    };
                    break;
                case LOCAL_VAR:
                    ret = new Symbol.VarSymbol(sym.flags() & FINAL, sym.name, sym.type, translatedSym) {
                        @Override
                        public Symbol baseSymbol() {
                            //keep mapping with original symbol
                            return sym;
                        }
                    };
                    ((Symbol.VarSymbol) ret).pos = ((Symbol.VarSymbol) sym).pos;
                    // If sym.data == ElementKind.EXCEPTION_PARAMETER,
                    // set ret.data = ElementKind.EXCEPTION_PARAMETER too.
                    // Because method com.sun.tools.javac.jvm.Code.fillExceptionParameterPositions and
                    // com.sun.tools.javac.jvm.Code.fillLocalVarPosition would use it.
                    // See JDK-8257740 for more information.
                    if (((Symbol.VarSymbol) sym).isExceptionParameter()) {
                        ((Symbol.VarSymbol) ret).setData(ElementKind.EXCEPTION_PARAMETER);
                    }
                    break;
                case PARAM:
                    ret = new Symbol.VarSymbol((sym.flags() & FINAL) | PARAMETER, sym.name, sym.type, translatedSym);
                    ((Symbol.VarSymbol) ret).pos = ((Symbol.VarSymbol) sym).pos;
                    // Set ret.data. Same as case LOCAL_VAR above.
                    if (((Symbol.VarSymbol) sym).isExceptionParameter()) {
                        ((Symbol.VarSymbol) ret).setData(ElementKind.EXCEPTION_PARAMETER);
                    }
                    break;
                default:
                    Assert.error(skind.name());
                    throw new AssertionError();
            }
            if (ret != sym && skind.propagateAnnotations()) {
                ret.setDeclarationAttributes(sym.getRawAttributes());
                ret.setTypeAttributes(sym.getRawTypeAttributes());
            }
            return ret;
        }

        void addSymbol(Symbol sym, LambdaCompiler.LambdaSymbolKind skind) {
            if (skind == CAPTURED_THIS && sym != null && sym.kind == TYP && !typesUnderConstruction.isEmpty()) {
                Symbol.ClassSymbol currentClass = currentClass();
                if (currentClass != null && typesUnderConstruction.contains(currentClass)) {
                    // reference must be to enclosing outer instance, mutate capture kind.
                    Assert.check(sym != currentClass); // should have been caught right in Attr
                    skind = CAPTURED_OUTER_THIS;
                }
            }
            Map<Symbol, Symbol> transMap = getSymbolMap(skind);
            if (!transMap.containsKey(sym)) {
                transMap.put(sym, translate(sym, skind));
            }
        }

        Map<Symbol, Symbol> getSymbolMap(LambdaCompiler.LambdaSymbolKind skind) {
            Map<Symbol, Symbol> m = translatedSymbols.get(skind);
            Assert.checkNonNull(m);
            return m;
        }

        JCTree translate(JCTree.JCIdent lambdaIdent) {
            for (LambdaCompiler.LambdaSymbolKind kind : LambdaCompiler.LambdaSymbolKind.values()) {
                Map<Symbol, Symbol> m = getSymbolMap(kind);
                switch (kind) {
                    default:
                        if (m.containsKey(lambdaIdent.sym)) {
                            Symbol tSym = m.get(lambdaIdent.sym);
                            JCTree t = lambdaCompiler.makeIdent(tSym).setType(lambdaIdent.type);
                            return t;
                        }
                        break;
                    case CAPTURED_OUTER_THIS:
                        Optional<Symbol> proxy = m.keySet().stream()
                                .filter(out -> lambdaIdent.sym.isMemberOf(out.type.tsym, lambdaCompiler.types))
                                .reduce((a, b) -> a.isEnclosedBy((Symbol.ClassSymbol) b) ? a : b);
                        if (proxy.isPresent()) {
                            // Transform outer instance variable references anchoring them to the captured synthetic.
                            Symbol tSym = m.get(proxy.get());
                            JCTree.JCExpression t = lambdaCompiler.makeIdent(tSym).setType(lambdaIdent.sym.owner.type);
                            t = lambdaCompiler.make.Select(t, lambdaIdent.sym);
                            t.setType(lambdaIdent.type);
                            TreeInfo.setSymbol(t, lambdaIdent.sym);
                            return t;
                        }
                        break;
                }
            }
            return null;
        }

        /* Translate away qualified this expressions, anchoring them to synthetic parameters that
           capture the qualified this handle. `fieldAccess' is guaranteed to one such.
        */
        public JCTree translate(JCTree.JCFieldAccess fieldAccess) {
            Assert.check(fieldAccess.name == lambdaCompiler.names._this);
            Map<Symbol, Symbol> m = translatedSymbols.get(LambdaCompiler.LambdaSymbolKind.CAPTURED_OUTER_THIS);
            if (m.containsKey(fieldAccess.sym.owner)) {
                Symbol tSym = m.get(fieldAccess.sym.owner);
                JCTree.JCExpression t = lambdaCompiler.makeIdent(tSym).setType(fieldAccess.sym.owner.type);
                return t;
            }
            return null;
        }

        /* Translate away naked new instance creation expressions with implicit enclosing instances,
           anchoring them to synthetic parameters that stand proxy for the qualified outer this handle.
        */
        public JCTree.JCNewClass translate(JCTree.JCNewClass newClass) {
            Assert.check(newClass.clazz.type.tsym.hasOuterInstance() && newClass.encl == null);
            Map<Symbol, Symbol> m = translatedSymbols.get(LambdaCompiler.LambdaSymbolKind.CAPTURED_OUTER_THIS);
            final Type enclosingType = newClass.clazz.type.getEnclosingType();
            if (m.containsKey(enclosingType.tsym)) {
                Symbol tSym = m.get(enclosingType.tsym);
                JCTree.JCExpression encl = lambdaCompiler.makeIdent(tSym).setType(enclosingType);
                newClass.encl = encl;
            }
            return newClass;
        }

        /**
         * The translatedSym is not complete/accurate until the analysis is
         * finished.  Once the analysis is finished, the translatedSym is
         * "completed" -- updated with type information, access modifiers,
         * and full parameter list.
         */
        void complete() {
            if (syntheticParams != null) {
                return;
            }
            boolean inInterface = translatedSym.owner.isInterface();
            boolean thisReferenced = !getSymbolMap(CAPTURED_THIS).isEmpty();

            // If instance access isn't needed, make it static.
            // Interface instance methods must be default methods.
            // Lambda methods are private synthetic.
            // Inherit ACC_STRICT from the enclosing method, or, for clinit,
            // from the class.
            translatedSym.flags_field = SYNTHETIC | LAMBDA_METHOD |
                    owner.flags_field & STRICTFP |
                    owner.owner.flags_field & STRICTFP |
                    PRIVATE |
                    (thisReferenced ? (inInterface ? DEFAULT : 0) : STATIC);

            //compute synthetic params
            ListBuffer<JCTree.JCVariableDecl> params = new ListBuffer<>();
            ListBuffer<Symbol.VarSymbol> parameterSymbols = new ListBuffer<>();

            // The signature of the method is augmented with the following
            // synthetic parameters:
            //
            // 1) reference to enclosing contexts captured by the lambda expression
            // 2) enclosing locals captured by the lambda expression
            for (Symbol thisSym : getSymbolMap(CAPTURED_VAR).values()) {
                params.append(lambdaCompiler.make.VarDef((Symbol.VarSymbol) thisSym, null));
                parameterSymbols.append((Symbol.VarSymbol) thisSym);
            }
            for (Symbol thisSym : getSymbolMap(CAPTURED_OUTER_THIS).values()) {
                params.append(lambdaCompiler.make.VarDef((Symbol.VarSymbol) thisSym, null));
                parameterSymbols.append((Symbol.VarSymbol) thisSym);
            }
            for (Symbol thisSym : getSymbolMap(PARAM).values()) {
                params.append(lambdaCompiler.make.VarDef((Symbol.VarSymbol) thisSym, null));
                parameterSymbols.append((Symbol.VarSymbol) thisSym);
            }
            syntheticParams = params.toList();

            translatedSym.params = parameterSymbols.toList();

            // Compute and set the lambda name
            translatedSym.name = lambdaName();

            //prepend synthetic args to translated lambda method signature
            translatedSym.type = lambdaCompiler.types.createMethodTypeWithParameters(
                    generatedLambdaSig(),
                    TreeInfo.types(syntheticParams));
        }

        Type generatedLambdaSig() {
            return tree.getDescriptorType(lambdaCompiler.types);
        }
    }

    /**
     * This class retains all the useful information about a method reference;
     * the contents of this class are filled by the LambdaAnalyzer visitor,
     * and the used by the main translation routines in order to adjust method
     * references (i.e. in case a bridge is needed)
     */
    final class ReferenceTranslationContext extends TranslationContext<JCTree.JCMemberReference> {

        final boolean isSuper;

        ReferenceTranslationContext(JCTree.JCMemberReference tree) {
            super(tree);
            this.isSuper = tree.hasKind(JCTree.JCMemberReference.ReferenceKind.SUPER);
        }

        boolean needsVarArgsConversion() {
            return tree.varargsElement != null;
        }

        /**
         * @return Is this an array operation like clone()
         */
        boolean isArrayOp() {
            return tree.sym.owner == lambdaCompiler.syms.arrayClass;
        }

        boolean receiverAccessible() {
            //hack needed to workaround 292 bug (7087658)
            //when 292 issue is fixed we should remove this and change the backend
            //code to always generate a method handle to an accessible method
            return tree.ownerAccessible;
        }

        /**
         * This method should be called only when target release <= 14
         * where LambdaMetaFactory does not spin nestmate classes.
         * <p>
         * This method should be removed when --release 14 is not supported.
         */
        boolean isPrivateInOtherClass() {
            assert !lambdaCompiler.nestmateLambdas;
            return (tree.sym.flags() & PRIVATE) != 0 &&
                    !lambdaCompiler.types.isSameType(
                            tree.sym.enclClass().asType(),
                            owner.enclClass().asType());
        }

        /**
         * Erasure destroys the implementation parameter subtype
         * relationship for intersection types.
         * Have similar problems for union types too.
         */
        boolean interfaceParameterIsIntersectionOrUnionType() {
            List<Type> tl = tree.getDescriptorType(lambdaCompiler.types).getParameterTypes();
            for (; tl.nonEmpty(); tl = tl.tail) {
                Type pt = tl.head;
                if (isIntersectionOrUnionType(pt))
                    return true;
            }
            return false;
        }

        boolean isIntersectionOrUnionType(Type t) {
            switch (t.getKind()) {
                case INTERSECTION:
                case UNION:
                    return true;
                case TYPEVAR:
                    Type.TypeVar tv = (Type.TypeVar) t;
                    return isIntersectionOrUnionType(tv.getUpperBound());
            }
            return false;
        }

        /**
         * Does this reference need to be converted to a lambda
         * (i.e. var args need to be expanded or "super" is used)
         */
        final boolean needsConversionToLambda() {
            return interfaceParameterIsIntersectionOrUnionType() ||
                    isSuper ||
                    needsVarArgsConversion() ||
                    isArrayOp() ||
                    (!lambdaCompiler.nestmateLambdas && isPrivateInOtherClass()) ||
                    lambdaCompiler.isProtectedInSuperClassOfEnclosingClassInOtherPackage(tree.sym, owner) ||
                    !receiverAccessible() ||
                    (tree.getMode() == MemberReferenceTree.ReferenceMode.NEW &&
                            tree.kind != JCTree.JCMemberReference.ReferenceKind.ARRAY_CTOR &&
                            (tree.sym.owner.isDirectlyOrIndirectlyLocal() || tree.sym.owner.isInner()));
        }

        Type generatedRefSig() {
            return tree.sym.type;
        }

        Type bridgedRefSig() {
            return lambdaCompiler.types.findDescriptorSymbol(tree.target.tsym).type;
        }
    }
}
