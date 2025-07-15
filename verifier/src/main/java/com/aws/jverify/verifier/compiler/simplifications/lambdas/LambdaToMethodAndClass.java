/*
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.aws.jverify.verifier.compiler.simplifications.lambdas;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.MethodHandleSymbol;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.jvm.PoolConstant.LoadableConstant;
//import com.sun.tools.javac.resources.CompilerProperties.Errors;
//import com.sun.tools.javac.resources.CompilerProperties.Fragments;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree.JCMemberReference.ReferenceKind;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.DynamicMethodSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.Type.TypeVar;
import com.aws.jverify.verifier.compiler.simplifications.lambdas.LambdaToMethodAndClass.LambdaAnalyzerPreprocessor.*;
//import com.sun.tools.javac.comp.Lower.BasicFreeVarCollector;
//import com.sun.tools.javac.resources.CompilerProperties.Notes;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.source.tree.MemberReferenceTree.ReferenceMode;

import java.awt.*;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.aws.jverify.verifier.compiler.simplifications.lambdas.LambdaToMethodAndClass.LambdaSymbolKind.*;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.Kind.*;
import static com.sun.tools.javac.code.TypeTag.*;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeKind;

import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.util.List;

/**
 * This pass desugars lambda expressions into static methods
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class LambdaToMethodAndClass extends TreeTranslator {

    private Enter enter;
    private Attr attr;
    private JCDiagnostic.Factory diags;
    private Log log;
    private Lower lower;
    private Names names;
    private Symtab syms;
    private Resolve rs;
    private Operators operators;
    private TreeMaker make;
    private Types types;
    private TransTypes transTypes;
    private Env<AttrContext> attrEnv;

    /** the analyzer scanner */
    private LambdaAnalyzerPreprocessor analyzer;

    /** map from lambda trees to translation contexts */
    private Map<JCTree, TranslationContext<?>> contextMap;

    /** current translation context (visitor argument) */
    private TranslationContext<?> context;

    /** info about the current class being processed */
    private KlassInfo kInfo;

    /** dump statistics about lambda code generation */
    private final boolean dumpLambdaToMethodStats;

    /** true if line or local variable debug info has been requested */
    private final boolean debugLinesOrVars;

    /** dump statistics about lambda method deduplication */
    private final boolean verboseDeduplication;

    /** deduplicate lambda implementation methods */
    private final boolean deduplicateLambdas;

    /** lambda proxy is a dynamic nestmate */
    private final boolean nestmateLambdas;

    /** Flag for alternate metafactories indicating the lambda object has multiple targets */
    public static final int FLAG_MARKERS = 1 << 1;

    /** Flag for alternate metafactories indicating the lambda object requires multiple bridges */
    public static final int FLAG_BRIDGES = 1 << 2;

    // <editor-fold defaultstate="collapsed" desc="Instantiating">
    protected static final Context.Key<LambdaToMethodAndClass> unlambdaKey = new Context.Key<>();

    public static LambdaToMethodAndClass instance(Context context) {
        LambdaToMethodAndClass instance = context.get(unlambdaKey);
        if (instance == null) {
            instance = new LambdaToMethodAndClass(context);
        }
        return instance;
    }
    private LambdaToMethodAndClass(Context context) {
        context.put(unlambdaKey, this);
        diags = JCDiagnostic.Factory.instance(context);
        log = Log.instance(context);
        enter = Enter.instance(context);
        lower = Lower.instance(context);
        names = Names.instance(context);
        syms = Symtab.instance(context);
        rs = Resolve.instance(context);
        operators = Operators.instance(context);
        make = TreeMaker.instance(context);
        types = Types.instance(context);
        transTypes = TransTypes.instance(context);
        analyzer = new LambdaAnalyzerPreprocessor();
        Options options = Options.instance(context);
        dumpLambdaToMethodStats = options.isSet("debug.dumpLambdaToMethodStats");
        attr = Attr.instance(context);
        boolean lineDebugInfo =
                options.isUnset(Option.G_CUSTOM) ||
                        options.isSet(Option.G_CUSTOM, "lines");
        boolean varDebugInfo =
                options.isUnset(Option.G_CUSTOM)
                        ? options.isSet(Option.G)
                        : options.isSet(Option.G_CUSTOM, "vars");
        debugLinesOrVars = lineDebugInfo || varDebugInfo;
        verboseDeduplication = options.isSet("debug.dumpLambdaToMethodDeduplication");
        deduplicateLambdas = options.getBoolean("deduplicateLambdas", true);
        nestmateLambdas = Target.instance(context).runtimeUseNestAccess();
    }
    // </editor-fold>

    class DedupedLambda {
        private final MethodSymbol symbol;
        private final JCTree tree;

        private int hashCode;

        DedupedLambda(MethodSymbol symbol, JCTree tree) {
            this.symbol = symbol;
            this.tree = tree;
        }


        @Override
        public int hashCode() {
            int hashCode = this.hashCode;
            if (hashCode == 0) {
                this.hashCode = hashCode = TreeHasher.hash(tree, symbol.params());
            }
            return hashCode;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof DedupedLambda dedupedLambda)
                    && types.isSameType(symbol.asType(), dedupedLambda.symbol.asType())
                    && new TreeDiffer(symbol.params(), dedupedLambda.symbol.params()).scan(tree, dedupedLambda.tree);
        }
    }

    private class KlassInfo {

        /**
         * list of methods to append
         */
        private ListBuffer<JCTree> appendedMethodList;

        private Map<DedupedLambda, DedupedLambda> dedupedLambdas;

        private Map<Object, DynamicMethodSymbol> dynMethSyms = new HashMap<>();

        private final JCClassDecl clazz;

        private KlassInfo(JCClassDecl clazz) {
            this.clazz = clazz;
            appendedMethodList = new ListBuffer<>();
            dedupedLambdas = new HashMap<>();
            MethodType type = new MethodType(List.of(syms.serializedLambdaType), syms.objectType,
                    List.nil(), syms.methodClass);
        }

        private void addMethod(JCTree decl) {
            appendedMethodList = appendedMethodList.prepend(decl);
        }
    }

    // <editor-fold defaultstate="collapsed" desc="translate methods">
    @Override
    public <T extends JCTree> T translate(T tree) {
        TranslationContext<?> newContext = contextMap.get(tree);
        return translate(tree, newContext != null ? newContext : context);
    }

    <T extends JCTree> T translate(T tree, TranslationContext<?> newContext) {
        TranslationContext<?> prevContext = context;
        try {
            context = newContext;
            return super.translate(tree);
        }
        finally {
            context = prevContext;
        }
    }

    <T extends JCTree> List<T> translate(List<T> trees, TranslationContext<?> newContext) {
        ListBuffer<T> buf = new ListBuffer<>();
        for (T tree : trees) {
            buf.append(translate(tree, newContext));
        }
        return buf.toList();
    }

    public JCTree translateTopLevelClass(Env<AttrContext> env, JCTree cdef, TreeMaker make) {
        this.make = make;
        this.attrEnv = env;
        this.context = null;
        this.contextMap = new HashMap<>();
        return translate(cdef);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="visitor methods">
    /**
     * Visit a class.
     * Maintain the translatedMethodList across nested classes.
     * Append the translatedMethodList to the class after it is translated.
     * @param tree
     */
    @Override
    public void visitClassDef(JCClassDecl tree) {
        if (tree.sym.owner.kind == PCK) {
            //analyze class
            tree = analyzer.analyzeAndPreprocessClass(tree);
        }
        KlassInfo prevKlassInfo = kInfo;
        try {
            kInfo = new KlassInfo(tree);
            super.visitClassDef(tree);
            //add all translated instance methods here
            List<JCTree> newMethods = kInfo.appendedMethodList.toList();
            tree.defs = tree.defs.appendList(newMethods);
            for (JCTree lambda : newMethods) {
                tree.sym.members().enter(((JCClassDecl)lambda).sym);
            }
            result = tree;
        } finally {
            kInfo = prevKlassInfo;
        }
    }

    // where
    // Reassign type annotations from the source that should really belong to the lambda
    private void apportionTypeAnnotations(JCLambda tree,
                                          Supplier<List<Attribute.TypeCompound>> source,
                                          Consumer<List<Attribute.TypeCompound>> owner,
                                          Consumer<List<Attribute.TypeCompound>> lambda) {

        ListBuffer<Attribute.TypeCompound> ownerTypeAnnos = new ListBuffer<>();
        ListBuffer<Attribute.TypeCompound> lambdaTypeAnnos = new ListBuffer<>();

        for (Attribute.TypeCompound tc : source.get()) {
            if (tc.position.onLambda == tree) {
                lambdaTypeAnnos.append(tc);
            } else {
                ownerTypeAnnos.append(tc);
            }
        }
        if (lambdaTypeAnnos.nonEmpty()) {
            owner.accept(ownerTypeAnnos.toList());
            lambda.accept(lambdaTypeAnnos.toList());
        }
    }

    private JCIdent makeThis(Type type, Symbol owner) {
        VarSymbol _this = new VarSymbol(PARAMETER | FINAL | SYNTHETIC,
                names._this,
                type,
                owner);
        return makeIdent(_this);
    }

    /**
     * Translate a method reference into an invokedynamic call to the
     * meta-factory.
     * @param tree
     */
    @Override
    public void visitReference(JCMemberReference tree) {
        throw new RuntimeException();
    }

    /**
     * Translate identifiers within a lambda to the mapped identifier
     * @param tree
     */
    @Override
    public void visitIdent(JCIdent tree) {
        if (context == null || !analyzer.lambdaIdentSymbolFilter(tree.sym)) {
            super.visitIdent(tree);
        } else {
            int prevPos = make.pos;
            try {
                make.at(tree);

                LambdaTranslationContext lambdaContext = (LambdaTranslationContext) context;
                JCTree ltree = lambdaContext.translate(tree);
                if (ltree != null) {
                    result = ltree;
                } else {
                    //access to untranslated symbols (i.e. compile-time constants,
                    //members defined inside the lambda body, etc.) )
                    super.visitIdent(tree);
                }
            } finally {
                make.at(prevPos);
            }
        }
    }

    /**
     * Translate qualified `this' references within a lambda to the mapped identifier
     * @param tree
     */
    @Override
    public void visitSelect(JCFieldAccess tree) {
        if (context == null || !analyzer.lambdaFieldAccessFilter(tree)) {
            super.visitSelect(tree);
        } else {
            int prevPos = make.pos;
            try {
                make.at(tree);

                LambdaTranslationContext lambdaContext = (LambdaTranslationContext) context;
                JCTree ltree = lambdaContext.translate(tree);
                if (ltree != null) {
                    result = ltree;
                } else {
                    super.visitSelect(tree);
                }
            } finally {
                make.at(prevPos);
            }
        }
    }

    /**
     * Translate instance creation expressions with implicit enclosing instances
     * @param tree
     */
    @Override
    public void visitNewClass(JCNewClass tree) {
        if (context == null || !analyzer.lambdaNewClassFilter(context, tree)) {
            super.visitNewClass(tree);
        } else {
            int prevPos = make.pos;
            try {
                make.at(tree);

                LambdaTranslationContext lambdaContext = (LambdaTranslationContext) context;
                tree = lambdaContext.translate(tree);
                super.visitNewClass(tree);
            } finally {
                make.at(prevPos);
            }
        }
    }

    @Override
    public void visitVarDef(JCVariableDecl tree) {
        LambdaTranslationContext lambdaContext = (LambdaTranslationContext)context;
        if (context != null && lambdaContext.getSymbolMap(LOCAL_VAR).containsKey(tree.sym)) {
            tree.init = translate(tree.init);
            tree.sym = (VarSymbol) lambdaContext.getSymbolMap(LOCAL_VAR).get(tree.sym);
            result = tree;
        } else {
            super.visitVarDef(tree);
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Translation helper methods">

    private JCBlock makeLambdaBody(JCLambda tree, JCMethodDecl lambdaMethodDecl) {
        return tree.getBodyKind() == JCLambda.BodyKind.EXPRESSION ?
                makeLambdaExpressionBody((JCExpression)tree.body, lambdaMethodDecl) :
                makeLambdaStatementBody((JCBlock)tree.body, lambdaMethodDecl, tree.canCompleteNormally);
    }

    private JCBlock makeLambdaExpressionBody(JCExpression expr, JCMethodDecl lambdaMethodDecl) {
        Type restype = lambdaMethodDecl.type.getReturnType();
        boolean isLambda_void = expr.type.hasTag(VOID);
        boolean isTarget_void = restype.hasTag(VOID);
        boolean isTarget_Void = types.isSameType(restype, types.boxedClass(syms.voidType).type);
        int prevPos = make.pos;
        try {
            if (isTarget_void) {
                //target is void:
                // BODY;
                JCStatement stat = make.at(expr).Exec(expr);
                return make.Block(0, List.of(stat));
            } else if (isLambda_void && isTarget_Void) {
                //void to Void conversion:
                // BODY; return null;
                ListBuffer<JCStatement> stats = new ListBuffer<>();
                stats.append(make.at(expr).Exec(expr));
                stats.append(make.Return(make.Literal(BOT, null).setType(syms.botType)));
                return make.Block(0, stats.toList());
            } else {
                //non-void to non-void conversion:
                // return BODY;
                return make.at(expr).Block(0, List.of(make.Return(expr)));
            }
        } finally {
            make.at(prevPos);
        }
    }

    private JCBlock makeLambdaStatementBody(JCBlock block, final JCMethodDecl lambdaMethodDecl, boolean completeNormally) {
        final Type restype = lambdaMethodDecl.type.getReturnType();
        final boolean isTarget_void = restype.hasTag(VOID);
        boolean isTarget_Void = types.isSameType(restype, types.boxedClass(syms.voidType).type);

        class LambdaBodyTranslator extends TreeTranslator {

            @Override
            public void visitClassDef(JCClassDecl tree) {
                //do NOT recurse on any inner classes
                result = tree;
            }

            @Override
            public void visitLambda(JCLambda tree) {
                //do NOT recurse on any nested lambdas
                result = tree;
            }

            @Override
            public void visitReturn(JCReturn tree) {
                boolean isLambda_void = tree.expr == null;
                if (isTarget_void && !isLambda_void) {
                    //Void to void conversion:
                    // { TYPE $loc = RET-EXPR; return; }
                    VarSymbol loc = makeSyntheticVar(0, names.fromString("$loc"), tree.expr.type, lambdaMethodDecl.sym);
                    JCVariableDecl varDef = make.VarDef(loc, tree.expr);
                    result = make.Block(0, List.of(varDef, make.Return(null)));
                } else {
                    result = tree;
                }

            }
        }

        JCBlock trans_block = new LambdaBodyTranslator().translate(block);
        if (completeNormally && isTarget_Void) {
            //there's no return statement and the lambda (possibly inferred)
            //return type is java.lang.Void; emit a synthetic return statement
            trans_block.stats = trans_block.stats.append(make.Return(make.Literal(BOT, null).setType(syms.botType)));
        }
        return trans_block;
    }

    /** Make an attributed class instance creation expression.
     *  @param ctype    The class type.
     *  @param args     The constructor arguments.
     *  @param cons     The constructor symbol
     */
    JCNewClass makeNewClass(Type ctype, List<JCExpression> args, Symbol cons) {
        JCNewClass tree = make.NewClass(null,
                null, make.QualIdent(ctype.tsym), args, null);
        tree.constructor = cons;
        tree.type = ctype;
        return tree;
    }

    /**
     * Create new synthetic method with given flags, name, type, owner
     */
    private MethodSymbol makePrivateSyntheticMethod(long flags, Name name, Type type, Symbol owner) {
        return new MethodSymbol(flags | SYNTHETIC | PRIVATE, name, type, owner);
    }

    /**
     * Create new synthetic variable with given flags, name, type, owner
     */
    private VarSymbol makeSyntheticVar(long flags, Name name, Type type, Symbol owner) {
        return new VarSymbol(flags | SYNTHETIC, name, type, owner);
    }

    /**
     * Set varargsElement field on a given tree (must be either a new class tree
     * or a method call tree)
     */
    private void setVarargsIfNeeded(JCTree tree, Type varargsElement) {
        if (varargsElement != null) {
            switch (tree.getTag()) {
                case APPLY: ((JCMethodInvocation)tree).varargsElement = varargsElement; break;
                case NEWCLASS: ((JCNewClass)tree).varargsElement = varargsElement; break;
                case TYPECAST: setVarargsIfNeeded(((JCTypeCast) tree).expr, varargsElement); break;
                default: throw new AssertionError();
            }
        }
    }

    /**
     * Convert method/constructor arguments by inserting appropriate cast
     * as required by type-erasure - this is needed when bridging a lambda/method
     * reference, as the bridged signature might require downcast to be compatible
     * with the generated signature.
     */
    private List<JCExpression> convertArgs(Symbol meth, List<JCExpression> args, Type varargsElement) {
        Assert.check(meth.kind == MTH);
        List<Type> formals = meth.type.getParameterTypes();
        if (varargsElement != null) {
            Assert.check((meth.flags() & VARARGS) != 0);
        }
        return transTypes.translateArgs(args, formals, varargsElement, attrEnv);
    }

    // </editor-fold>

    /**
     * Converts a method reference which cannot be used directly into a lambda
     */
    private class MemberReferenceToLambda {

        private final JCMemberReference tree;
        private final ReferenceTranslationContext localContext;
        private final Symbol owner;
        private final ListBuffer<JCExpression> args = new ListBuffer<>();
        private final ListBuffer<JCVariableDecl> params = new ListBuffer<>();

        private JCExpression receiverExpression = null;

        MemberReferenceToLambda(JCMemberReference tree, ReferenceTranslationContext localContext, Symbol owner) {
            this.tree = tree;
            this.localContext = localContext;
            this.owner = owner;
        }

        JCLambda lambda() {
            int prevPos = make.pos;
            try {
                make.at(tree);

                //body generation - this can be either a method call or a
                //new instance creation expression, depending on the member reference kind
                VarSymbol rcvr = addParametersReturnReceiver();
                JCExpression expr = (tree.getMode() == ReferenceMode.INVOKE)
                        ? expressionInvoke(rcvr)
                        : expressionNew();

                JCLambda slam = make.Lambda(params.toList(), expr);
                slam.target = tree.target;
                slam.type = tree.type;
                slam.pos = tree.pos;
                return slam;
            } finally {
                make.at(prevPos);
            }
        }

        /**
         * Generate the parameter list for the converted member reference.
         *
         * @return The receiver variable symbol, if any
         */
        VarSymbol addParametersReturnReceiver() {
            Type samDesc = localContext.bridgedRefSig();
            List<Type> samPTypes = samDesc.getParameterTypes();
            List<Type> descPTypes = tree.getDescriptorType(types).getParameterTypes();

            // Determine the receiver, if any
            VarSymbol rcvr;
            switch (tree.kind) {
                case BOUND:
                    // The receiver is explicit in the method reference
                    rcvr = addParameter("rec$", tree.getQualifierExpression().type, false);
                    receiverExpression = attr.makeNullCheck(tree.getQualifierExpression());
                    break;
                case UNBOUND:
                    // The receiver is the first parameter, extract it and
                    // adjust the SAM and unerased type lists accordingly
                    rcvr = addParameter("rec$", samDesc.getParameterTypes().head, false);
                    samPTypes = samPTypes.tail;
                    descPTypes = descPTypes.tail;
                    break;
                default:
                    rcvr = null;
                    break;
            }
            List<Type> implPTypes = tree.sym.type.getParameterTypes();
            int implSize = implPTypes.size();
            int samSize = samPTypes.size();
            // Last parameter to copy from referenced method, exclude final var args
            int last = localContext.needsVarArgsConversion() ? implSize - 1 : implSize;

            // Failsafe -- assure match-up
            boolean checkForIntersection = tree.varargsElement != null || implSize == descPTypes.size();

            // Use parameter types of the implementation method unless the unerased
            // SAM parameter type is an intersection type, in that case use the
            // erased SAM parameter type so that the supertype relationship
            // the implementation method parameters is not obscured.
            // Note: in this loop, the lists implPTypes, samPTypes, and descPTypes
            // are used as pointers to the current parameter type information
            // and are thus not usable afterwards.
            for (int i = 0; implPTypes.nonEmpty() && i < last; ++i) {
                // By default use the implementation method parameter type
                Type parmType = implPTypes.head;
                if (checkForIntersection) {
                    if (descPTypes.head.getKind() == TypeKind.INTERSECTION) {
                        parmType = samPTypes.head;
                    }
                    // If the unerased parameter type is a type variable whose
                    // bound is an intersection (eg. <T extends A & B>) then
                    // use the SAM parameter type
                    if (descPTypes.head.getKind() == TypeKind.TYPEVAR) {
                        TypeVar tv = (TypeVar) descPTypes.head;
                        if (tv.getUpperBound().getKind() == TypeKind.INTERSECTION) {
                            parmType = samPTypes.head;
                        }
                    }
                }
                addParameter("x$" + i, parmType, true);

                // Advance to the next parameter
                implPTypes = implPTypes.tail;
                samPTypes = samPTypes.tail;
                descPTypes = descPTypes.tail;
            }
            // Flatten out the var args
            for (int i = last; i < samSize; ++i) {
                addParameter("xva$" + i, tree.varargsElement, true);
            }

            return rcvr;
        }

        JCExpression getReceiverExpression() {
            return receiverExpression;
        }

        private JCExpression makeReceiver(VarSymbol rcvr) {
            if (rcvr == null) return null;
            JCExpression rcvrExpr = makeIdent(rcvr);
            boolean protAccess =
                    isProtectedInSuperClassOfEnclosingClassInOtherPackage(tree.sym, owner);
            Type rcvrType = tree.ownerAccessible && !protAccess ? tree.sym.enclClass().type
                    : tree.expr.type;
            if (rcvrType == syms.arrayClass.type) {
                // Map the receiver type to the actually type, not just "array"
                rcvrType = tree.getQualifierExpression().type;
            }
            if (!rcvr.type.tsym.isSubClass(rcvrType.tsym, types)) {
                rcvrExpr = make.TypeCast(make.Type(rcvrType), rcvrExpr).setType(rcvrType);
            }
            return rcvrExpr;
        }

        /**
         * determine the receiver of the method call - the receiver can
         * be a type qualifier, the synthetic receiver parameter or 'super'.
         */
        private JCExpression expressionInvoke(VarSymbol rcvr) {
            JCExpression qualifier =
                    (rcvr != null) ?
                            makeReceiver(rcvr) :
                            tree.getQualifierExpression();

            //create the qualifier expression
            JCFieldAccess select = make.Select(qualifier, tree.sym);
            select.sym = tree.sym;
            select.type = tree.sym.erasure(types);

            //create the method call expression
            JCExpression apply = make.Apply(List.nil(), select,
                            convertArgs(tree.sym, args.toList(), tree.varargsElement)).
                    setType(tree.sym.erasure(types).getReturnType());

            apply = transTypes.coerce(attrEnv, apply,
                    localContext.tree.referentType.getReturnType());

            setVarargsIfNeeded(apply, tree.varargsElement);
            return apply;
        }

        /**
         * Lambda body to use for a 'new'.
         */
        private JCExpression expressionNew() {
            if (tree.kind == ReferenceKind.ARRAY_CTOR) {
                //create the array creation expression
                JCNewArray newArr = make.NewArray(
                        make.Type(types.elemtype(tree.getQualifierExpression().type)),
                        List.of(makeIdent(params.first().sym)),
                        null);
                newArr.type = tree.getQualifierExpression().type;
                return newArr;
            } else {
                //create the instance creation expression
                //note that method reference syntax does not allow an explicit
                //enclosing class (so the enclosing class is null)
                // but this may need to be patched up later with the proxy for the outer this
                JCNewClass newClass = make.NewClass(null,
                        List.nil(),
                        make.Type(tree.getQualifierExpression().type),
                        convertArgs(tree.sym, args.toList(), tree.varargsElement),
                        null);
                newClass.constructor = tree.sym;
                newClass.constructorType = tree.sym.erasure(types);
                newClass.type = tree.getQualifierExpression().type;
                setVarargsIfNeeded(newClass, tree.varargsElement);
                return newClass;
            }
        }

        private VarSymbol addParameter(String name, Type p, boolean genArg) {
            VarSymbol vsym = new VarSymbol(PARAMETER | SYNTHETIC, names.fromString(name), p, owner);
            vsym.pos = tree.pos;
            params.append(make.VarDef(vsym, null));
            if (genArg) {
                args.append(makeIdent(vsym));
            }
            return vsym;
        }
    }

    private MethodType typeToMethodType(Type mt) {
        Type type = mt;
        return new MethodType(type.getParameterTypes(),
                type.getReturnType(),
                type.getThrownTypes(),
                syms.methodClass);
    }

    // <editor-fold defaultstate="collapsed" desc="Lambda/reference analyzer">
    /**
     * This visitor collects information about translation of a lambda expression.
     * More specifically, it keeps track of the enclosing contexts and captured locals
     * accessed by the lambda being translated (as well as other useful info).
     * It also translates away problems for LambdaToMethod.
     */
    class LambdaAnalyzerPreprocessor extends TreeTranslator {

        /** the frame stack - used to reconstruct translation info about enclosing scopes */
        private List<Frame> frameStack;

        /**
         * keep the count of lambda expression (used to generate unambiguous
         * names)
         */
        private int lambdaCount = 0;

        /**
         * List of types undergoing construction via explicit constructor chaining.
         */
        private List<ClassSymbol> typesUnderConstruction;

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

        private Map<Symbol, JCClassDecl> localClassDefs;

        /**
         * maps for fake clinit symbols to be used as owners of lambda occurring in
         * a static var init context
         */
        private Map<ClassSymbol, Symbol> clinits = new HashMap<>();

        private JCClassDecl analyzeAndPreprocessClass(JCClassDecl tree) {
            frameStack = List.nil();
            typesUnderConstruction = List.nil();
            localClassDefs = new HashMap<>();
            return translate(tree);
        }

        @Override
        public void visitApply(JCMethodInvocation tree) {
            List<ClassSymbol> previousNascentTypes = typesUnderConstruction;
            try {
                Name methName = TreeInfo.name(tree.meth);
                if (methName == names._this || methName == names._super) {
                    typesUnderConstruction = typesUnderConstruction.prepend(currentClass());
                }
                super.visitApply(tree);
            } finally {
                typesUnderConstruction = previousNascentTypes;
            }
        }
        // where
        private ClassSymbol currentClass() {
            for (Frame frame : frameStack) {
                if (frame.tree.hasTag(JCTree.Tag.CLASSDEF)) {
                    JCClassDecl cdef = (JCClassDecl) frame.tree;
                    return cdef.sym;
                }
            }
            return null;
        }

        @Override
        public void visitBlock(JCBlock tree) {
            List<Frame> prevStack = frameStack;
            try {
                if (frameStack.nonEmpty() && frameStack.head.tree.hasTag(CLASSDEF)) {
                    frameStack = frameStack.prepend(new Frame(tree));
                }
                super.visitBlock(tree);
            }
            finally {
                frameStack = prevStack;
            }
        }

        @Override
        public void visitClassDef(JCClassDecl tree) {
            List<Frame> prevStack = frameStack;
            int prevLambdaCount = lambdaCount;
            SyntheticMethodNameCounter prevSyntheticMethodNameCounts =
                    syntheticMethodNameCounts;
            Map<ClassSymbol, Symbol> prevClinits = clinits;
            DiagnosticSource prevSource = log.currentSource();
            try {
                log.useSource(tree.sym.sourcefile);
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
                        final TypeSymbol outerInstanceSymbol = tree.sym.type.getEnclosingType().tsym;
                        while (localContext != null && !localContext.owner.isStatic()) {
                            if (localContext.tree.hasTag(LAMBDA)) {
                                JCTree block = capturedDecl(localContext.depth, outerInstanceSymbol);
                                if (block == null) break;
                                ((LambdaTranslationContext)localContext)
                                        .addSymbol(outerInstanceSymbol, CAPTURED_THIS);
                            }
                            localContext = localContext.prev;
                        }
                    }
                }
                frameStack = frameStack.prepend(new Frame(tree));
                super.visitClassDef(tree);
            }
            finally {
                log.useSource(prevSource.getFile());
                frameStack = prevStack;
                lambdaCount = prevLambdaCount;
                syntheticMethodNameCounts = prevSyntheticMethodNameCounts;
                clinits = prevClinits;
            }
        }

        @Override
        public void visitIdent(JCIdent tree) {
            if (context() != null && lambdaIdentSymbolFilter(tree.sym)) {
                if (tree.sym.kind == VAR &&
                        tree.sym.owner.kind == MTH &&
                        tree.type.constValue() == null) {
                    TranslationContext<?> localContext = context();
                    while (localContext != null) {
                        if (localContext.tree.getTag() == LAMBDA) {
                            JCTree block = capturedDecl(localContext.depth, tree.sym);
                            if (block == null) break;
                            ((LambdaTranslationContext)localContext)
                                    .addSymbol(tree.sym, CAPTURED_VAR);
                        }
                        localContext = localContext.prev;
                    }
                } else if (tree.sym.owner.kind == TYP) {
                    TranslationContext<?> localContext = context();
                    while (localContext != null  && !localContext.owner.isStatic()) {
                        if (localContext.tree.hasTag(LAMBDA)) {
                            JCTree block = capturedDecl(localContext.depth, tree.sym);
                            if (block == null) break;
                            switch (block.getTag()) {
                                case CLASSDEF:
                                    JCClassDecl cdecl = (JCClassDecl)block;
                                    ((LambdaTranslationContext)localContext)
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
        public void visitLambda(JCLambda tree) {
            analyzeLambda(tree, "lambda.stat");
        }

        private void analyzeLambda(JCLambda tree, JCExpression methodReferenceReceiver) {
            // Translation of the receiver expression must occur first
            JCExpression rcvr = translate(methodReferenceReceiver);
            LambdaTranslationContext context = analyzeLambda(tree, "mref.stat.1");
            if (rcvr != null) {
                context.methodReferenceReceiver = rcvr;
            }
        }

        private LambdaTranslationContext analyzeLambda(JCLambda tree, String statKey) {
            List<Frame> prevStack = frameStack;
            try {
                LambdaTranslationContext context = new LambdaTranslationContext(tree);
                frameStack = frameStack.prepend(new Frame(tree));
                for (JCVariableDecl param : tree.params) {
                    context.addSymbol(param.sym, PARAM);
                    frameStack.head.addLocal(param.sym);
                }
                contextMap.put(tree, context);
                super.visitLambda(tree);
                context.complete();
                if (dumpLambdaToMethodStats) {
                    log.note(tree, diags.noteKey(statKey, context.needsAltMetafactory(), context.translatedSym));
                }
                return context;
            }
            finally {
                frameStack = prevStack;
            }
        }

        @Override
        public void visitMethodDef(JCMethodDecl tree) {
            List<Frame> prevStack = frameStack;
            try {
                frameStack = frameStack.prepend(new Frame(tree));
                super.visitMethodDef(tree);
            }
            finally {
                frameStack = prevStack;
            }
        }

        @Override
        public void visitNewClass(JCNewClass tree) {
            TypeSymbol def = tree.type.tsym;
            boolean inReferencedClass = currentlyInClass(def);
            boolean isLocal = def.isDirectlyOrIndirectlyLocal();
            if ((inReferencedClass && isLocal || lambdaNewClassFilter(context(), tree))) {
                TranslationContext<?> localContext = context();
                final TypeSymbol outerInstanceSymbol = tree.type.getEnclosingType().tsym;
                while (localContext != null  && !localContext.owner.isStatic()) {
                    if (localContext.tree.hasTag(LAMBDA)) {
                        if (outerInstanceSymbol != null) {
                            JCTree block = capturedDecl(localContext.depth, outerInstanceSymbol);
                            if (block == null) break;
                        }
                        ((LambdaTranslationContext)localContext)
                                .addSymbol(outerInstanceSymbol, CAPTURED_THIS);
                    }
                    localContext = localContext.prev;
                }
            }
            super.visitNewClass(tree);
            if (context() != null && !inReferencedClass && isLocal) {
                LambdaTranslationContext lambdaContext = (LambdaTranslationContext)context();
                captureLocalClassDefs(def, lambdaContext);
            }
        }
        //where
        void captureLocalClassDefs(Symbol csym, final LambdaTranslationContext lambdaContext) {
//            JCClassDecl localCDef = localClassDefs.get(csym);
//            if (localCDef != null && lambdaContext.freeVarProcessedLocalClasses.add(csym)) {
//                BasicFreeVarCollector fvc = lower.new BasicFreeVarCollector() {
//                    @Override
//                    void addFreeVars(ClassSymbol c) {
//                        captureLocalClassDefs(c, lambdaContext);
//                    }
//                    @Override
//                    void visitSymbol(Symbol sym) {
//                        if (sym.kind == VAR &&
//                                sym.owner.kind == MTH &&
//                                ((VarSymbol)sym).getConstValue() == null) {
//                            TranslationContext<?> localContext = context();
//                            while (localContext != null) {
//                                if (localContext.tree.getTag() == LAMBDA) {
//                                    JCTree block = capturedDecl(localContext.depth, sym);
//                                    if (block == null) break;
//                                    ((LambdaTranslationContext)localContext).addSymbol(sym, CAPTURED_VAR);
//                                }
//                                localContext = localContext.prev;
//                            }
//                        }
//                    }
//                };
//                fvc.scan(localCDef);
//            }
        }
        //where
        boolean currentlyInClass(Symbol csym) {
            for (Frame frame : frameStack) {
                if (frame.tree.hasTag(JCTree.Tag.CLASSDEF)) {
                    JCClassDecl cdef = (JCClassDecl) frame.tree;
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
        public void visitReference(JCMemberReference tree) {
            ReferenceTranslationContext rcontext = new ReferenceTranslationContext(tree);
            contextMap.put(tree, rcontext);
            if (rcontext.needsConversionToLambda()) {
                // Convert to a lambda, and process as such
                MemberReferenceToLambda conv = new MemberReferenceToLambda(tree, rcontext, owner());
                analyzeLambda(conv.lambda(), conv.getReceiverExpression());
            } else {
                super.visitReference(tree);
                if (dumpLambdaToMethodStats) {
                    // TODO
//                    log.note(tree, Notes.MrefStat(rcontext.needsAltMetafactory(), null));
                }
            }
        }

        @Override
        public void visitSelect(JCFieldAccess tree) {
            if (context() != null && tree.sym.kind == VAR &&
                    (tree.sym.name == names._this ||
                            tree.sym.name == names._super)) {
                // A select of this or super means, if we are in a lambda,
                // we much have an instance context
                TranslationContext<?> localContext = context();
                while (localContext != null  && !localContext.owner.isStatic()) {
                    if (localContext.tree.hasTag(LAMBDA)) {
                        JCClassDecl clazz = (JCClassDecl)capturedDecl(localContext.depth, tree.sym);
                        if (clazz == null) break;
                        ((LambdaTranslationContext)localContext).addSymbol(clazz.sym, CAPTURED_THIS);
                    }
                    localContext = localContext.prev;
                }
            }
            super.visitSelect(tree);
        }

        @Override
        public void visitVarDef(JCVariableDecl tree) {
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
            }
            finally {
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
                        if (((JCVariableDecl)frameStack2.head.tree).sym.isDirectlyOrIndirectlyLocal()) {
                            frameStack2 = frameStack2.tail;
                            break;
                        }
                        JCClassDecl cdecl = (JCClassDecl)frameStack2.tail.head.tree;
                        return initSym(cdecl.sym,
                                ((JCVariableDecl)frameStack2.head.tree).sym.flags() & STATIC);
                    case BLOCK:
                        JCClassDecl cdecl2 = (JCClassDecl)frameStack2.tail.head.tree;
                        return initSym(cdecl2.sym,
                                ((JCBlock)frameStack2.head.tree).flags & STATIC);
                    case CLASSDEF:
                        return ((JCClassDecl)frameStack2.head.tree).sym;
                    case METHODDEF:
                        return ((JCMethodDecl)frameStack2.head.tree).sym;
                    case LAMBDA:
                        if (!skipLambda)
                            return ((LambdaTranslationContext)contextMap
                                    .get(frameStack2.head.tree)).translatedSym;
                    default:
                        frameStack2 = frameStack2.tail;
                }
            }
            Assert.error();
            return null;
        }

        private Symbol initSym(ClassSymbol csym, long flags) {
            boolean isStatic = (flags & STATIC) != 0;
            if (isStatic) {
                /* static clinits are generated in Gen, so we need to use a fake
                 * one. Attr creates a fake clinit method while attributing
                 * lambda expressions used as initializers of static fields, so
                 * let's use that one.
                 */
                MethodSymbol clinit = attr.removeClinit(csym);
                if (clinit != null) {
                    clinits.put(csym, clinit);
                    return clinit;
                }

                /* if no clinit is found at Attr, then let's try at clinits.
                 */
                clinit = (MethodSymbol)clinits.get(csym);
                if (clinit == null) {
                    /* no luck, let's create a new one
                     */
                    clinit = makePrivateSyntheticMethod(STATIC,
                            names.clinit,
                            new MethodType(List.nil(), syms.voidType,
                                    List.nil(), syms.methodClass),
                            csym);
                    clinits.put(csym, clinit);
                }
                return clinit;
            } else {
                //get the first constructor and treat it as the instance init sym
                for (Symbol s : csym.members_field.getSymbolsByName(names.init)) {
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
                        ClassSymbol clazz = ((JCClassDecl)block.tree).sym;
                        if (clazz.isSubClass(sym, types) || sym.isMemberOf(clazz, types)) {
                            return currentDepth > depth ? null : block.tree;
                        }
                        break;
                    case VARDEF:
                        if ((((JCVariableDecl)block.tree).sym == sym &&
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
                TranslationContext<?> context = contextMap.get(frame.tree);
                if (context != null) {
                    return context;
                }
            }
            return null;
        }

        /**
         *  This is used to filter out those identifiers that needs to be adjusted
         *  when translating away lambda expressions
         */
        private boolean lambdaIdentSymbolFilter(Symbol sym) {
            return (sym.kind == VAR || sym.kind == MTH)
                    && !sym.isStatic()
                    && sym.name != names.init;
        }

        /**
         *  This is used to filter out those select nodes that need to be adjusted
         *  when translating away lambda expressions - at the moment, this is the
         *  set of nodes that select `this' (qualified this)
         */
        private boolean lambdaFieldAccessFilter(JCFieldAccess fAccess) {
            return (context instanceof LambdaTranslationContext lambdaContext)
                    && !fAccess.sym.isStatic()
                    && fAccess.name == names._this
                    && (fAccess.sym.owner.kind == TYP)
                    && !lambdaContext.translatedSymbols.get(CAPTURED_OUTER_THIS).isEmpty();
        }

        /**
         * This is used to filter out those new class expressions that need to
         * be qualified with an enclosing tree
         */
        private boolean lambdaNewClassFilter(TranslationContext<?> context, JCNewClass tree) {
            if (context != null
                    && tree.encl == null
                    && tree.def == null
                    && !tree.type.getEnclosingType().hasTag(NONE)) {
                Type encl = tree.type.getEnclosingType();
                Type current = context.owner.enclClass().type;
                while (!current.hasTag(NONE)) {
                    if (current.tsym.isSubClass(encl.tsym, types)) {
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
        abstract class TranslationContext<T extends JCFunctionalExpression> {

            /** the underlying (untranslated) tree */
            final T tree;

            /** points to the adjusted enclosing scope in which this lambda/mref expression occurs */
            final Symbol owner;

            /** the depth of this lambda expression in the frame stack */
            final int depth;

            /** the enclosing translation context (set for nested lambdas/mref) */
            final TranslationContext<?> prev;

            /** list of methods to be bridged by the meta-factory */
            final List<Symbol> bridges;

            TranslationContext(T tree) {
                this.tree = tree;
                this.owner = owner(true);
                this.depth = frameStack.size() - 1;
                this.prev = context();
                ClassSymbol csym =
                        types.makeFunctionalInterfaceClass(attrEnv, names.empty, tree.target, ABSTRACT | INTERFACE);
                this.bridges = types.functionalInterfaceBridges(csym);
            }

            /** does this functional expression need to be created using alternate metafactory? */
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
        class LambdaTranslationContext extends TranslationContext<JCLambda> {

            /** variable in the enclosing context to which this lambda is assigned */
            final Symbol self;

            /** variable in the enclosing context to which this lambda is assigned */
            final Symbol assignedTo;

            Map<LambdaSymbolKind, Map<Symbol, Symbol>> translatedSymbols;

            /** the synthetic symbol for the method hoisting the translated lambda */
            MethodSymbol translatedSym;

            List<JCVariableDecl> syntheticParams;

            /**
             * to prevent recursion, track local classes processed
             */
            final Set<Symbol> freeVarProcessedLocalClasses;

            /**
             * For method references converted to lambdas.  The method
             * reference receiver expression. Must be treated like a captured
             * variable.
             */
            JCExpression methodReferenceReceiver;

            LambdaTranslationContext(JCLambda tree) {
                super(tree);
                Frame frame = frameStack.head;
                switch (frame.tree.getTag()) {
                    case VARDEF:
                        assignedTo = self = ((JCVariableDecl) frame.tree).sym;
                        break;
                    case ASSIGN:
                        self = null;
                        assignedTo = TreeInfo.symbol(((JCAssign) frame.tree).getVariable());
                        break;
                    default:
                        assignedTo = self = null;
                        break;
                }

                // This symbol will be filled-in in complete
                if (owner.kind == MTH) {
                    final MethodSymbol originalOwner = (MethodSymbol)owner.clone(owner.owner);
                    this.translatedSym = new MethodSymbol(SYNTHETIC | PRIVATE, null, null, owner.enclClass()) {
                        @Override
                        public MethodSymbol originalEnclosingMethod() {
                            return originalOwner;
                        }
                    };
                } else {
                    this.translatedSym = makePrivateSyntheticMethod(0, null, null, owner.enclClass());
                }
                translatedSymbols = new EnumMap<>(LambdaSymbolKind.class);

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
                return names.lambda.append(names.fromString(enclosingMethodName() + "$" + lambdaCount++));
            }

            /**
             * Translate a symbol of a given kind into something suitable for the
             * synthetic lambda body
             */
            Symbol translate(final Symbol sym, LambdaSymbolKind skind) {
                Symbol ret;
                switch (skind) {
                    case CAPTURED_THIS:
                        ret = sym;  // self represented
                        break;
                    case CAPTURED_VAR:
                        ret = new VarSymbol(SYNTHETIC | FINAL | PARAMETER, sym.name, sym.type, translatedSym) {
                            @Override
                            public Symbol baseSymbol() {
                                //keep mapping with original captured symbol
                                return sym;
                            }
                        };
                        break;
                    case CAPTURED_OUTER_THIS:
                        Name name = names.fromString(sym.flatName().toString().replace('.', '$') + names.dollarThis);
                        ret = new VarSymbol(SYNTHETIC | FINAL | PARAMETER, name, sym.type, translatedSym) {
                            @Override
                            public Symbol baseSymbol() {
                                //keep mapping with original captured symbol
                                return sym;
                            }
                        };
                        break;
                    case LOCAL_VAR:
                        ret = new VarSymbol(sym.flags() & FINAL, sym.name, sym.type, translatedSym) {
                            @Override
                            public Symbol baseSymbol() {
                                //keep mapping with original symbol
                                return sym;
                            }
                        };
                        ((VarSymbol) ret).pos = ((VarSymbol) sym).pos;
                        // If sym.data == ElementKind.EXCEPTION_PARAMETER,
                        // set ret.data = ElementKind.EXCEPTION_PARAMETER too.
                        // Because method com.sun.tools.javac.jvm.Code.fillExceptionParameterPositions and
                        // com.sun.tools.javac.jvm.Code.fillLocalVarPosition would use it.
                        // See JDK-8257740 for more information.
                        if (((VarSymbol) sym).isExceptionParameter()) {
                            ((VarSymbol) ret).setData(ElementKind.EXCEPTION_PARAMETER);
                        }
                        break;
                    case PARAM:
                        ret = new VarSymbol((sym.flags() & FINAL) | PARAMETER, sym.name, sym.type, translatedSym);
                        ((VarSymbol) ret).pos = ((VarSymbol) sym).pos;
                        // Set ret.data. Same as case LOCAL_VAR above.
                        if (((VarSymbol) sym).isExceptionParameter()) {
                            ((VarSymbol) ret).setData(ElementKind.EXCEPTION_PARAMETER);
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

            void addSymbol(Symbol sym, LambdaSymbolKind skind) {
                if (skind == CAPTURED_THIS && sym != null && sym.kind == TYP && !typesUnderConstruction.isEmpty()) {
                    ClassSymbol currentClass = currentClass();
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

            Map<Symbol, Symbol> getSymbolMap(LambdaSymbolKind skind) {
                Map<Symbol, Symbol> m = translatedSymbols.get(skind);
                Assert.checkNonNull(m);
                return m;
            }

            JCTree translate(JCIdent lambdaIdent) {
                for (LambdaSymbolKind kind : LambdaSymbolKind.values()) {
                    Map<Symbol, Symbol> m = getSymbolMap(kind);
                    switch(kind) {
                        default:
                            if (m.containsKey(lambdaIdent.sym)) {
                                Symbol tSym = m.get(lambdaIdent.sym);
                                JCTree t = makeIdent(tSym).setType(lambdaIdent.type);
                                return t;
                            }
                            break;
                        case CAPTURED_OUTER_THIS:
                            Optional<Symbol> proxy = m.keySet().stream()
                                    .filter(out -> lambdaIdent.sym.isMemberOf(out.type.tsym, types))
                                    .reduce((a, b) -> a.isEnclosedBy((ClassSymbol)b) ? a : b);
                            if (proxy.isPresent()) {
                                // Transform outer instance variable references anchoring them to the captured synthetic.
                                Symbol tSym = m.get(proxy.get());
                                JCExpression t = makeIdent(tSym).setType(lambdaIdent.sym.owner.type);
                                t = make.Select(t, lambdaIdent.sym);
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
            public JCTree translate(JCFieldAccess fieldAccess) {
                Assert.check(fieldAccess.name == names._this);
                Map<Symbol, Symbol> m = translatedSymbols.get(LambdaSymbolKind.CAPTURED_OUTER_THIS);
                if (m.containsKey(fieldAccess.sym.owner)) {
                    Symbol tSym = m.get(fieldAccess.sym.owner);
                    JCExpression t = makeIdent(tSym).setType(fieldAccess.sym.owner.type);
                    return t;
                }
                return null;
            }

            /* Translate away naked new instance creation expressions with implicit enclosing instances,
               anchoring them to synthetic parameters that stand proxy for the qualified outer this handle.
            */
            public JCNewClass translate(JCNewClass newClass) {
                Assert.check(newClass.clazz.type.tsym.hasOuterInstance() && newClass.encl == null);
                Map<Symbol, Symbol> m = translatedSymbols.get(LambdaSymbolKind.CAPTURED_OUTER_THIS);
                final Type enclosingType = newClass.clazz.type.getEnclosingType();
                if (m.containsKey(enclosingType.tsym)) {
                    Symbol tSym = m.get(enclosingType.tsym);
                    JCExpression encl = makeIdent(tSym).setType(enclosingType);
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
                        (thisReferenced? (inInterface? DEFAULT : 0) : STATIC);

                //compute synthetic params
                ListBuffer<JCVariableDecl> params = new ListBuffer<>();
                ListBuffer<VarSymbol> parameterSymbols = new ListBuffer<>();

                // The signature of the method is augmented with the following
                // synthetic parameters:
                //
                // 1) reference to enclosing contexts captured by the lambda expression
                // 2) enclosing locals captured by the lambda expression
                for (Symbol thisSym : getSymbolMap(CAPTURED_VAR).values()) {
                    params.append(make.VarDef((VarSymbol) thisSym, null));
                    parameterSymbols.append((VarSymbol) thisSym);
                }
                for (Symbol thisSym : getSymbolMap(CAPTURED_OUTER_THIS).values()) {
                    params.append(make.VarDef((VarSymbol) thisSym, null));
                    parameterSymbols.append((VarSymbol) thisSym);
                }
                for (Symbol thisSym : getSymbolMap(PARAM).values()) {
                    params.append(make.VarDef((VarSymbol) thisSym, null));
                    parameterSymbols.append((VarSymbol) thisSym);
                }
                syntheticParams = params.toList();

                translatedSym.params = parameterSymbols.toList();

                // Compute and set the lambda name
                translatedSym.name = lambdaName();

                //prepend synthetic args to translated lambda method signature
                translatedSym.type = types.createMethodTypeWithParameters(
                        generatedLambdaSig(),
                        TreeInfo.types(syntheticParams));
            }

            Type generatedLambdaSig() {
                return tree.getDescriptorType(types);
            }
        }

        /**
         * This class retains all the useful information about a method reference;
         * the contents of this class are filled by the LambdaAnalyzer visitor,
         * and the used by the main translation routines in order to adjust method
         * references (i.e. in case a bridge is needed)
         */
        final class ReferenceTranslationContext extends TranslationContext<JCMemberReference> {

            final boolean isSuper;

            ReferenceTranslationContext(JCMemberReference tree) {
                super(tree);
                this.isSuper = tree.hasKind(ReferenceKind.SUPER);
            }

            boolean needsVarArgsConversion() {
                return tree.varargsElement != null;
            }

            /**
             * @return Is this an array operation like clone()
             */
            boolean isArrayOp() {
                return tree.sym.owner == syms.arrayClass;
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
             *
             * This method should be removed when --release 14 is not supported.
             */
            boolean isPrivateInOtherClass() {
                assert !nestmateLambdas;
                return  (tree.sym.flags() & PRIVATE) != 0 &&
                        !types.isSameType(
                                tree.sym.enclClass().asType(),
                                owner.enclClass().asType());
            }

            /**
             * Erasure destroys the implementation parameter subtype
             * relationship for intersection types.
             * Have similar problems for union types too.
             */
            boolean interfaceParameterIsIntersectionOrUnionType() {
                List<Type> tl = tree.getDescriptorType(types).getParameterTypes();
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
                        TypeVar tv = (TypeVar) t;
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
                        (!nestmateLambdas && isPrivateInOtherClass()) ||
                        isProtectedInSuperClassOfEnclosingClassInOtherPackage(tree.sym, owner) ||
                        !receiverAccessible() ||
                        (tree.getMode() == ReferenceMode.NEW &&
                                tree.kind != ReferenceKind.ARRAY_CTOR &&
                                (tree.sym.owner.isDirectlyOrIndirectlyLocal() || tree.sym.owner.isInner()));
            }

            Type generatedRefSig() {
                return tree.sym.type;
            }

            Type bridgedRefSig() {
                return types.findDescriptorSymbol(tree.target.tsym).type;
            }
        }
    }

    private JCIdent makeIdent(Symbol tSym) {
        var result = make.Ident(tSym);
        result.sym = tSym;
        return result;
    }
    // </editor-fold>

    /*
     * These keys provide mappings for various translated lambda symbols
     * and the prevailing order must be maintained.
     */
    enum LambdaSymbolKind {
        PARAM,          // original to translated lambda parameters
        LOCAL_VAR,      // original to translated lambda locals
        CAPTURED_VAR,   // variables in enclosing scope to translated synthetic parameters
        CAPTURED_THIS,  // class symbols to translated synthetic parameters (for captured member access)
        CAPTURED_OUTER_THIS; // used when `this' capture is illegal, but outer this capture is legit (JDK-8129740)

        boolean propagateAnnotations() {
            switch (this) {
                case CAPTURED_VAR:
                case CAPTURED_THIS:
                case CAPTURED_OUTER_THIS:
                    return false;
                default:
                    return true;
            }
        }
    }

    private boolean isProtectedInSuperClassOfEnclosingClassInOtherPackage(Symbol targetReference,
                                                                          Symbol currentClass) {
        return ((targetReference.flags() & PROTECTED) != 0 &&
                targetReference.packge() != currentClass.packge());
    }

    // Add this field to the main class
    private int lambdaClassCount = 0;

    /**
     * Generate a class that implements the functional interface for a lambda
     */
    private JCClassDecl generateLambdaClass(LambdaTranslationContext localContext) {
        JCLambda tree = localContext.tree;

        // Create synthetic class name
        Name className = names.fromString(kInfo.clazz.name + "$Lambda$" + (++lambdaClassCount));

        // Get the functional interface type
        Type functionalInterface = tree.target;

        // Find the single abstract method (SAM)
        MethodSymbol samMethod = (MethodSymbol) types.findDescriptorSymbol(functionalInterface.tsym);

        // Create class symbol
        ClassSymbol classSym = new ClassSymbol(
                FINAL | SYNTHETIC | PRIVATE,
                className,
                functionalInterface,
                kInfo.clazz.sym
        );

        // Set up class members
        classSym.members_field = Scope.WriteableScope.create(classSym);
        classSym.completer = Symbol.Completer.NULL_COMPLETER;

        // Create constructor
        JCMethodDecl constructor = generateLambdaConstructor(localContext, classSym);

        // Create the method that implements the functional interface
        JCMethodDecl samImpl = generateSAMImplementation(localContext, classSym, samMethod);

        // Create fields for captured variables
        List<JCTree> capturedFields = generateCapturedFields(localContext, classSym);

        // Build the class declaration
        ListBuffer<JCTree> classMembers = new ListBuffer<>();
        classMembers.appendList(capturedFields);
        classMembers.append(constructor);
        classMembers.append(samImpl);

        JCClassDecl classDecl = make.ClassDef(
                make.Modifiers(FINAL | SYNTHETIC | PRIVATE),
                className,
                List.nil(), // no type parameters
                make.Type(functionalInterface), // implements functional interface
                List.nil(), // no other interfaces
                classMembers.toList()
        );

        
        classDecl.sym = classSym;
        classDecl.type = classSym.type;
        
        classDecl.pos = context.tree.pos;
        
        return classDecl;
    }

    /**
     * Generate fields for captured variables
     */
    private List<JCTree> generateCapturedFields(LambdaTranslationContext localContext, ClassSymbol classSym) {
        ListBuffer<JCTree> fields = new ListBuffer<>();

        // Add fields for captured variables
        for (Map.Entry<Symbol, Symbol> entry : localContext.getSymbolMap(CAPTURED_VAR).entrySet()) {
            Symbol original = entry.getKey();

            // Create field symbol
            VarSymbol fieldSym = new VarSymbol(
                    FINAL | SYNTHETIC | PRIVATE,
                    original.name,
                    original.type,
                    classSym
            );

            // Create field declaration
            JCVariableDecl field = make.VarDef(fieldSym, null);
            fields.append(field);

            // Add to class members
            classSym.members().enter(fieldSym);
        }

        // Add fields for captured outer this
        for (Map.Entry<Symbol, Symbol> entry : localContext.getSymbolMap(CAPTURED_OUTER_THIS).entrySet()) {
            Symbol original = entry.getKey();

            VarSymbol fieldSym = new VarSymbol(
                    FINAL | SYNTHETIC | PRIVATE,
                    names.fromString(original.flatName().toString().replace('.', '$') + "$captured"),
                    original.type,
                    classSym
            );

            JCVariableDecl field = make.VarDef(fieldSym, null);
            fields.append(field);

            classSym.members().enter(fieldSym);
        }

        return fields.toList();
    }

    /**
     * Generate constructor that initializes captured variables
     */
    private JCMethodDecl generateLambdaConstructor(LambdaTranslationContext localContext, ClassSymbol classSym) {
        ListBuffer<JCVariableDecl> params = new ListBuffer<>();
        ListBuffer<JCStatement> body = new ListBuffer<>();

        // Add parameters and initialization for captured variables
        for (Map.Entry<Symbol, Symbol> entry : localContext.getSymbolMap(CAPTURED_VAR).entrySet()) {
            Symbol original = entry.getKey();

            // Constructor parameter
            VarSymbol paramSym = new VarSymbol(
                    FINAL | PARAMETER,
                    original.name,
                    original.type,
                    null // will be set when method is created
            );
            params.append(make.VarDef(paramSym, null));

            // Assignment in constructor body: this.field = param
            JCFieldAccess fieldAccess = make.Select(makeThis(classSym), original);
            JCAssign assign = make.Assign(fieldAccess, makeIdent(paramSym));
            body.append(make.Exec(assign));
        }

        // Handle captured outer this
        for (Map.Entry<Symbol, Symbol> entry : localContext.getSymbolMap(CAPTURED_OUTER_THIS).entrySet()) {
            Symbol original = entry.getKey();
            Name fieldName = names.fromString(original.flatName().toString().replace('.', '$') + "$captured");

            VarSymbol paramSym = new VarSymbol(
                    FINAL | PARAMETER,
                    fieldName,
                    original.type,
                    null
            );
            params.append(make.VarDef(paramSym, null));

            JCFieldAccess fieldAccess = make.Select(makeThis(classSym), original);
            JCAssign assign = make.Assign(fieldAccess, makeIdent(paramSym));
            body.append(make.Exec(assign));
        }

        // Create constructor method symbol
        MethodSymbol constructorSym = new MethodSymbol(
                PUBLIC | SYNTHETIC,
                names.init,
                new MethodType(
                        TreeInfo.types(params.toList()),
                        syms.voidType,
                        List.nil(),
                        syms.methodClass
                ),
                classSym
        );

        // Update parameter owners
        for (JCVariableDecl param : params) {
            param.sym.owner = constructorSym;
        }

        // Create constructor declaration
        JCMethodDecl constructor = make.MethodDef(
                constructorSym,
                make.Block(0, body.toList())
        );
        constructor.pos = localContext.tree.pos;

        classSym.members().enter(constructorSym);
        
        return constructor;
    }

    private JCIdent makeThis(ClassSymbol currentClassSymbol) {
        JCIdent result = make.Ident(names._this);

        VarSymbol thisSym = new VarSymbol(FINAL | SYNTHETIC, names._this, currentClassSymbol.type, currentClassSymbol);

        result.sym = thisSym;
        result.type = currentClassSymbol.type;

        return result;
    }

    /**
     * Generate the method that implements the functional interface
     */
    private JCMethodDecl generateSAMImplementation(LambdaTranslationContext localContext, ClassSymbol classSym, MethodSymbol samMethod) {
        JCLambda tree = localContext.tree;

        // Create method symbol for the SAM implementation
        MethodSymbol methodSym = new MethodSymbol(
                PUBLIC | SYNTHETIC,
                samMethod.name,
                samMethod.type,
                classSym
        );

        // Create parameters
        ListBuffer<JCVariableDecl> params = new ListBuffer<>();
        for (VarSymbol param : samMethod.params) {
            VarSymbol newParam = new VarSymbol(
                    param.flags() | PARAMETER,
                    param.name,
                    param.type,
                    methodSym
            );
            params.append(make.VarDef(newParam, null));
        }

        // Translate lambda body, replacing captured variable references with field accesses
        JCBlock lambdaBody = translateLambdaBodyForClass(tree, localContext, classSym, methodSym);

        // Create method declaration
        JCMethodDecl method = make.MethodDef(
                make.Modifiers(PUBLIC | SYNTHETIC),
                samMethod.name,
                make.Type(samMethod.getReturnType()),
                List.nil(),
                params.toList(),
                samMethod.getThrownTypes() == null ? List.nil() : make.Types(samMethod.getThrownTypes()),
                lambdaBody,
                null
        );

        method.sym = methodSym;
        method.type = methodSym.type;

        // Set the position to the lambda's position
        method.pos = tree.pos;

        classSym.members().enter(methodSym);

        return method;
    }

    /**
     * Translate lambda body for use in class, replacing captured vars with field access
     */
    private JCBlock translateLambdaBodyForClass(JCLambda tree, LambdaTranslationContext localContext,
                                                ClassSymbol classSym, MethodSymbol methodSym) {

        // Create a custom translator that replaces captured variable references
        TreeTranslator bodyTranslator = new TreeTranslator() {
            @Override
            public void visitIdent(JCIdent tree) {
                if (localContext.getSymbolMap(CAPTURED_VAR).containsKey(tree.sym)) {
                    // Replace with field access: this.capturedVar
                    JCFieldAccess fieldAccess = make.Select(makeThis(classSym), tree.sym);
                    fieldAccess.type = tree.type;
                    result = fieldAccess;
                } else {
                    super.visitIdent(tree);
                }
            }

            @Override
            public void visitSelect(JCFieldAccess tree) {
                if (tree.name == names._this && localContext.getSymbolMap(CAPTURED_OUTER_THIS).containsKey(tree.sym.owner)) {
                    // Replace outer this access with captured field
                    Name fieldName = names.fromString(tree.sym.owner.flatName().toString().replace('.', '$') + "$captured");
                    // TODO replace fieldName with symbol
                    JCFieldAccess fieldAccess = make.Select(makeThis(classSym), fieldName);
                    fieldAccess.type = tree.type;
                    result = fieldAccess;
                } else {
                    super.visitSelect(tree);
                }
            }
        };

        // Get the lambda body and translate it
        JCTree lambdaBodyTree = tree.body;
        JCTree translatedBody = bodyTranslator.translate(lambdaBodyTree);

        // Convert to block if needed
        if (tree.getBodyKind() == JCLambda.BodyKind.EXPRESSION) {
            JCExpression expr = (JCExpression) translatedBody;
            Type returnType = methodSym.getReturnType();

            if (returnType.hasTag(VOID)) {
                return make.Block(0, List.of(make.Exec(expr)));
            } else {
                return make.Block(0, List.of(make.Return(expr)));
            }
        } else {
            return (JCBlock) translatedBody;
        }
    }

    /**
     * Modified visitLambda to use class generation instead of metafactory
     */
    @Override
    public void visitLambda(JCLambda tree) {
        LambdaTranslationContext localContext = (LambdaTranslationContext)context;

        // Generate the lambda implementation class
        JCClassDecl lambdaClass = generateLambdaClass(localContext);

        // Add the class to the enclosing class
        kInfo.addMethod(lambdaClass);

        // Create instantiation of the lambda class
        result = generateLambdaClassInstantiation(localContext, lambdaClass);
    }

    /**
     * Generate constructor call to create lambda class instance
     */
    private JCExpression generateLambdaClassInstantiation(LambdaTranslationContext localContext, JCClassDecl lambdaClass) {
        ListBuffer<JCExpression> args = new ListBuffer<>();

        // Add captured variables as constructor arguments
        for (Symbol capturedVar : localContext.getSymbolMap(CAPTURED_VAR).keySet()) {
            if (capturedVar != localContext.self) {
                args.append(makeIdent(capturedVar));
            }
        }

        // Add captured outer this
        for (Symbol outerThis : localContext.getSymbolMap(CAPTURED_OUTER_THIS).keySet()) {
            args.append(make.QualThis(outerThis.type));
        }

        var constructor = (JCMethodDecl)lambdaClass.getMembers().get(lambdaClass.getMembers().size() - 2);

        JCNewClass newClass = make.NewClass(
                null,
                List.nil(),
                make.Ident(lambdaClass.sym),
                args.toList(),
                null
        );

        newClass.constructor = constructor.sym;
        newClass.type = lambdaClass.type;

        return newClass;
    }
}
