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
import com.sun.tools.javac.comp.*;
//import com.sun.tools.javac.resources.CompilerProperties.Errors;
//import com.sun.tools.javac.resources.CompilerProperties.Fragments;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.Type.TypeVar;
//import com.sun.tools.javac.comp.Lower.BasicFreeVarCollector;
//import com.sun.tools.javac.resources.CompilerProperties.Notes;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.util.*;

import java.util.HashMap;
import java.util.Map;

import static com.aws.jverify.verifier.compiler.simplifications.lambdas.LambdaCompiler.LambdaSymbolKind.*;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.Kind.*;
import static com.sun.tools.javac.code.TypeTag.*;

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
public class LambdaCompiler extends TreeTranslator {

    
    private Enter enter;
    public Attr attr;
    public JCDiagnostic.Factory diags;
    public Log log;
    private Lower lower;
    public Names names;
    public Symtab syms;
    private Resolve rs;
    private Operators operators;
    public TreeMaker make;
    public Types types;
    public TransTypes transTypes;
    public Env<AttrContext> attrEnv;

    /** the analyzer scanner */
    private LambdaAnalyzerPreprocessor analyzer;

    /** map from lambda trees to translation contexts */
    public Map<JCTree, TranslationContext<?>> contextMap;

    /** current translation context (visitor argument) */
    public TranslationContext<?> context;

    /** info about the current class being processed */
    private KlassInfo kInfo;

    /** dump statistics about lambda code generation */
    public final boolean dumpLambdaToMethodStats;

    /** true if line or local variable debug info has been requested */
    private final boolean debugLinesOrVars;

    /** dump statistics about lambda method deduplication */
    private final boolean verboseDeduplication;

    /** deduplicate lambda implementation methods */
    private final boolean deduplicateLambdas;

    /** lambda proxy is a dynamic nestmate */
    public final boolean nestmateLambdas;

    /** Flag for alternate metafactories indicating the lambda object has multiple targets */
    public static final int FLAG_MARKERS = 1 << 1;

    /** Flag for alternate metafactories indicating the lambda object requires multiple bridges */
    public static final int FLAG_BRIDGES = 1 << 2;

    // <editor-fold defaultstate="collapsed" desc="Instantiating">
    protected static final Context.Key<LambdaCompiler> unlambdaKey = new Context.Key<>();

    public static LambdaCompiler instance(Context context) {
        LambdaCompiler instance = context.get(unlambdaKey);
        if (instance == null) {
            instance = new LambdaCompiler(context);
        }
        return instance;
    }
    private LambdaCompiler(Context context) {
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
        analyzer = new LambdaAnalyzerPreprocessor(this);
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

    @Override
    public void visitReference(JCMemberReference tree) {
        throw new RuntimeException("Method reference should have already been translated to a Lambda");
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

    /**
     * Create new synthetic method with given flags, name, type, owner
     */
    public MethodSymbol makePrivateSyntheticMethod(long flags, Name name, Type type, Symbol owner) {
        return new MethodSymbol(flags | SYNTHETIC | PRIVATE, name, type, owner);
    }

    /**
     * Create new synthetic variable with given flags, name, type, owner
     */
    private VarSymbol makeSyntheticVar(long flags, Name name, Type type, Symbol owner) {
        return new VarSymbol(flags | SYNTHETIC, name, type, owner);
    }

    /**
     * Convert method/constructor arguments by inserting appropriate cast
     * as required by type-erasure - this is needed when bridging a lambda/method
     * reference, as the bridged signature might require downcast to be compatible
     * with the generated signature.
     */
    public List<JCExpression> convertArgs(Symbol meth, List<JCExpression> args, Type varargsElement) {
        Assert.check(meth.kind == MTH);
        List<Type> formals = meth.type.getParameterTypes();
        if (varargsElement != null) {
            Assert.check((meth.flags() & VARARGS) != 0);
        }
        return transTypes.translateArgs(args, formals, varargsElement, attrEnv);
    }

    // </editor-fold>

    private MethodType typeToMethodType(Type mt) {
        Type type = mt;
        return new MethodType(type.getParameterTypes(),
                type.getReturnType(),
                type.getThrownTypes(),
                syms.methodClass);
    }

    // <editor-fold defaultstate="collapsed" desc="Lambda/reference analyzer">

    public JCIdent makeIdent(Symbol tSym) {
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

    public boolean isProtectedInSuperClassOfEnclosingClassInOtherPackage(Symbol targetReference,
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
        ListBuffer<Type> from = new ListBuffer<>();
        ListBuffer<Type> to = new ListBuffer<>();
        types.adapt(functionalInterface.tsym.type, functionalInterface, from, to);
        var methodType = (MethodType)types.subst(samMethod.type, from.toList(),  to.toList());
        
        // Create class symbol
        ClassSymbol classSym = new ClassSymbol(
                FINAL | SYNTHETIC | PRIVATE | RECORD,
                className,
                functionalInterface,
                kInfo.clazz.sym
        );
        Type.ClassType classType = new Type.ClassType(Type.noType, List.nil(), classSym);
        classSym.type = classType;
        classType.supertype_field = functionalInterface;

        // Set up class members
        classSym.members_field = Scope.WriteableScope.create(classSym);
        classSym.completer = Symbol.Completer.NULL_COMPLETER;

        // Create constructor
        JCMethodDecl constructor = generateLambdaConstructor(localContext, classSym);

        // Create the method that implements the functional interface
        JCMethodDecl samImpl = generateSAMImplementation(localContext, classSym, samMethod, methodType);

        // Create fields for captured variables
        List<JCTree> capturedFields = generateCapturedFields(localContext, classSym);

        // Build the class declaration
        ListBuffer<JCTree> classMembers = new ListBuffer<>();
        classMembers.appendList(capturedFields);
        classMembers.append(constructor);
        classMembers.append(samImpl);

        List<JCTypeParameter> typeParameters = List.nil();
        for(var type : functionalInterface.getTypeArguments()) {
            if (type instanceof TypeVar var) {
                typeParameters = typeParameters.append(make.TypeParam(type.tsym.name, var));
            }   
        }
        JCClassDecl classDecl = make.ClassDef(
                make.Modifiers(FINAL | SYNTHETIC | PRIVATE | RECORD),
                className,
                typeParameters,
                make.Type(functionalInterface),
                List.nil(),
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

        for (Map.Entry<Symbol, Symbol> entry : localContext.getSymbolMap(CAPTURED_THIS).entrySet()) {
            Symbol original = entry.getKey();
            if (!(original instanceof VarSymbol)) {
                continue;
            }

            // Create field symbol
            VarSymbol fieldSym = new VarSymbol(
                    FINAL | SYNTHETIC | PRIVATE | RECORD,
                    names.fromString(original.flatName().toString().replace('.', '$') + "$captured"),
                    original.type,
                    classSym
            );

            // Create field declaration
            JCVariableDecl field = make.VarDef(fieldSym, null);
            fields.append(field);

            // Add to class members
            classSym.members().enter(fieldSym);
        }
        
        // Add fields for captured variables
        for (Map.Entry<Symbol, Symbol> entry : localContext.getSymbolMap(CAPTURED_VAR).entrySet()) {
            Symbol original = entry.getKey();

            // Create field symbol
            VarSymbol fieldSym = new VarSymbol(
                    FINAL | SYNTHETIC | PRIVATE | RECORD,
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
                    FINAL | SYNTHETIC | PRIVATE | RECORD,
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
            handleCaptureVariable(classSym, entry, params, body);
        }
        for (Map.Entry<Symbol, Symbol> entry : localContext.getSymbolMap(CAPTURED_THIS).entrySet()) {
            handleCaptureVariable(classSym, entry, params, body);
        }
        for (Map.Entry<Symbol, Symbol> entry : localContext.getSymbolMap(CAPTURED_OUTER_THIS).entrySet()) {
            handleCaptureVariable(classSym, entry, params, body);
        }

        // Create constructor method symbol
        MethodSymbol constructorSym = new MethodSymbol(
                PUBLIC | SYNTHETIC | RECORD,
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

    private void handleCaptureVariable(ClassSymbol classSym, Map.Entry<Symbol, Symbol> entry, ListBuffer<JCVariableDecl> params, ListBuffer<JCStatement> body) {
        Symbol original = entry.getKey();
        Symbol translatedSym = entry.getValue();

        if (original instanceof VarSymbol) {
            // Constructor parameter
            VarSymbol paramSym = new VarSymbol(
                    FINAL | PARAMETER,
                    translatedSym.name,
                    translatedSym.type,
                    null // will be set when method is created
            );
            params.append(make.VarDef(paramSym, null));

            // Assignment in constructor body: this.field = param
            JCFieldAccess fieldAccess = make.Select(makeThis(classSym), translatedSym);
            JCAssign assign = make.Assign(fieldAccess, makeIdent(paramSym));
            body.append(make.Exec(assign));
        }
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
    private JCMethodDecl generateSAMImplementation(LambdaTranslationContext localContext,
                                                   ClassSymbol classSym,
                                                   MethodSymbol samMethod, 
                                                   MethodType methodType) {
        JCLambda tree = localContext.tree;

        // Create method symbol for the SAM implementation
        MethodSymbol methodSym = new MethodSymbol(
                PUBLIC | SYNTHETIC,
                samMethod.name,
                methodType,
                classSym
        );

        // Create parameters
        ListBuffer<JCVariableDecl> params = new ListBuffer<>();
        for (var paramEntry : localContext.getSymbolMap(PARAM).entrySet()) {
            var param = paramEntry.getValue();
            VarSymbol newParam = new VarSymbol(
                    param.flags() | PARAMETER,
                    param.name,
                    param.type,
                    methodSym
            );
            methodSym.params().append(newParam);
            params.append(make.VarDef(newParam, null));
        }

        // Translate lambda body, replacing captured variable references with field accesses
        JCBlock lambdaBody = translateLambdaBodyForClass(tree, localContext, classSym, methodSym);

        // Create method declaration
        JCMethodDecl method = make.MethodDef(
                make.Modifiers(PUBLIC | SYNTHETIC),
                samMethod.name,
                make.Type(methodType.getReturnType()),
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
                    return;
                }

                var translatedThis = localContext.getSymbolMap(CAPTURED_THIS).get(tree.sym);
                if (translatedThis != null) {
                    // Replace with field access: this.capturedVar
                    JCFieldAccess fieldAccess = make.Select(makeThis(classSym), translatedThis);
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

        for (Symbol this_ : localContext.getSymbolMap(CAPTURED_THIS).keySet()) {
            args.append(make.This(this_.type));
        }
        
        // Add captured outer this
        for (Symbol outerThis : localContext.getSymbolMap(CAPTURED_OUTER_THIS).keySet()) {
            args.append(make.QualThis(outerThis.type));
        }

        var constructor = (JCMethodDecl)lambdaClass.getMembers().get(lambdaClass.getMembers().size() - 2);

        List<JCExpression> map = lambdaClass.getTypeParameters().map(t -> make.Type(t.type));
        JCNewClass newClass = make.NewClass(
                null,
                map,
                make.Ident(lambdaClass.sym),
                args.toList(),
                null
        );

        newClass.constructor = constructor.sym;
        newClass.type = lambdaClass.type;

        return newClass;
    }
}
