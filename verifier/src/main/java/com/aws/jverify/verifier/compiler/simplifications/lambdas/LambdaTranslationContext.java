package com.aws.jverify.verifier.compiler.simplifications.lambdas;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

import javax.lang.model.element.ElementKind;
import java.util.*;

import static com.aws.jverify.verifier.compiler.simplifications.lambdas.LambdaCompiler.LambdaSymbolKind.*;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.Kind.MTH;
import static com.sun.tools.javac.code.Kinds.Kind.TYP;

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

    LambdaTranslationContext(LambdaAnalyzerPreprocessor analyzerPreprocessor, JCTree.JCLambda tree) {
        super(analyzerPreprocessor, tree);
        Frame frame = lambdaAnalyzerPreprocessor.frameStack.head;
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
            this.translatedSym = lambdaAnalyzerPreprocessor.lambdaCompiler.makePrivateSyntheticMethod(0, null, null, owner.enclClass());
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
        return lambdaAnalyzerPreprocessor.lambdaCompiler.names.lambda.append(
                lambdaAnalyzerPreprocessor.lambdaCompiler.names.fromString(
                        enclosingMethodName() + "$" + lambdaAnalyzerPreprocessor.lambdaCount++));
    }

    /**
     * Translate a symbol of a given kind into something suitable for the
     * synthetic lambda body
     */
    Symbol translate(final Symbol sym, LambdaCompiler.LambdaSymbolKind skind) {
        Symbol ret;
        switch (skind) {
            case CAPTURED_THIS:
                ret = new Symbol.VarSymbol(SYNTHETIC | FINAL | PARAMETER,
                        sym.name, 
                        sym.type, translatedSym) {
                    @Override
                    public Symbol baseSymbol() {
                        //keep mapping with original captured symbol
                        return sym;
                    }
                };
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
                Name name = lambdaAnalyzerPreprocessor.lambdaCompiler.names.fromString(sym.flatName().toString().replace('.', '$') + lambdaAnalyzerPreprocessor.lambdaCompiler.names.dollarThis);
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
        if (skind == CAPTURED_THIS && sym != null && sym.kind == TYP && !lambdaAnalyzerPreprocessor.typesUnderConstruction.isEmpty()) {
            Symbol.ClassSymbol currentClass = lambdaAnalyzerPreprocessor.currentClass();
            if (currentClass != null && lambdaAnalyzerPreprocessor.typesUnderConstruction.contains(currentClass)) {
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
        for (LambdaCompiler.LambdaSymbolKind kind : values()) {
            Map<Symbol, Symbol> m = getSymbolMap(kind);
            switch (kind) {
                default:
                    if (m.containsKey(lambdaIdent.sym)) {
                        Symbol tSym = m.get(lambdaIdent.sym);
                        JCTree t = lambdaAnalyzerPreprocessor.lambdaCompiler.makeIdent(tSym).setType(lambdaIdent.type);
                        return t;
                    }
                    break;
                case CAPTURED_OUTER_THIS:
                    Optional<Symbol> proxy = m.keySet().stream()
                            .filter(out -> lambdaIdent.sym.isMemberOf(out.type.tsym, lambdaAnalyzerPreprocessor.lambdaCompiler.types))
                            .reduce((a, b) -> a.isEnclosedBy((Symbol.ClassSymbol) b) ? a : b);
                    if (proxy.isPresent()) {
                        // Transform outer instance variable references anchoring them to the captured synthetic.
                        Symbol tSym = m.get(proxy.get());
                        JCTree.JCExpression t = lambdaAnalyzerPreprocessor.lambdaCompiler.makeIdent(tSym).setType(lambdaIdent.sym.owner.type);
                        t = lambdaAnalyzerPreprocessor.lambdaCompiler.make.Select(t, lambdaIdent.sym);
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
        Assert.check(fieldAccess.name == lambdaAnalyzerPreprocessor.lambdaCompiler.names._this);
        Map<Symbol, Symbol> m = translatedSymbols.get(LambdaCompiler.LambdaSymbolKind.CAPTURED_OUTER_THIS);
        if (m.containsKey(fieldAccess.sym.owner)) {
            Symbol tSym = m.get(fieldAccess.sym.owner);
            JCTree.JCExpression t = lambdaAnalyzerPreprocessor.lambdaCompiler.makeIdent(tSym).setType(fieldAccess.sym.owner.type);
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
            JCTree.JCExpression encl = lambdaAnalyzerPreprocessor.lambdaCompiler.makeIdent(tSym).setType(enclosingType);
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
            params.append(lambdaAnalyzerPreprocessor.lambdaCompiler.make.VarDef((Symbol.VarSymbol) thisSym, null));
            parameterSymbols.append((Symbol.VarSymbol) thisSym);
        }
        for (Symbol thisSym : getSymbolMap(CAPTURED_OUTER_THIS).values()) {
            params.append(lambdaAnalyzerPreprocessor.lambdaCompiler.make.VarDef((Symbol.VarSymbol) thisSym, null));
            parameterSymbols.append((Symbol.VarSymbol) thisSym);
        }
        for (Symbol thisSym : getSymbolMap(PARAM).values()) {
            params.append(lambdaAnalyzerPreprocessor.lambdaCompiler.make.VarDef((Symbol.VarSymbol) thisSym, null));
            parameterSymbols.append((Symbol.VarSymbol) thisSym);
        }
        syntheticParams = params.toList();

        translatedSym.params = parameterSymbols.toList();

        // Compute and set the lambda name
        translatedSym.name = lambdaName();

        //prepend synthetic args to translated lambda method signature
        translatedSym.type = lambdaAnalyzerPreprocessor.lambdaCompiler.types.createMethodTypeWithParameters(
                generatedLambdaSig(),
                TreeInfo.types(syntheticParams));
    }

    Type generatedLambdaSig() {
        return tree.getDescriptorType(lambdaAnalyzerPreprocessor.lambdaCompiler.types);
    }
}
