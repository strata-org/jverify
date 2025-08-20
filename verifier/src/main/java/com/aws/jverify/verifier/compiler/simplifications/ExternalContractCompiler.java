package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.Contract;
import com.aws.jverify.Modifiable;
import com.aws.jverify.Verify;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.OverrideFinder;
import com.aws.jverify.verifier.compiler.frontend.JVerifyIndex;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.List;

import javax.lang.model.element.ElementKind;
import java.util.*;

import static com.aws.jverify.verifier.compiler.JavaToDafnyCompiler.isConstructor;

/**
 * Handles @Contract annotations, so that further passes can be agnostic to them.
 * Some remnants of the @Contract classes remain
 * <p>
 * For library contracts, the @Contract annotation is moved to the contractee symbol,
 * so that the immutable field can be found.
 * <p>
 * Also, while the header of the contract class is assigned the symbol of the contractee
 * The tree node itself is left unmodified since doing so was not necessary, and this might make debugging easier.
 * <p>
 * For source contracts, the contract definitions are moved to the contractee class,
 * and the contract class is deleted.
 */
public class ExternalContractCompiler {
    private final Names names;
    private final JVerifyIndex index;
    private final Enter enter;
    private final Types types;
    private final TreeMaker maker;
    private final Reporter reporter;
    private final JavacElements elements;
    private final Set<Symbol.ClassSymbol> symbolsWithContracts = new HashSet<>();
    private final Map<Symbol, Symbol> contractSymbolToContractee = new HashMap<>();

    public ExternalContractCompiler(Context context) {
        this.names = Names.instance(context);
        this.enter = Enter.instance(context);
        this.types = Types.instance(context);
        this.maker = TreeMaker.instance(context);
        this.elements = JavacElements.instance(context);
        this.index = JVerifyIndex.instance(context);
        this.reporter = Reporter.instance(context);
    }
    
    public Set<JCTree.JCCompilationUnit> apply(Set<JCTree.JCCompilationUnit> compilationUnits) {
        for(var unit : compilationUnits) {
            MoveSourceContracts moveSourceContracts = new MoveSourceContracts();
            moveSourceContracts.visitTopLevel(unit);
            java.util.List<JCTree> list = unit.defs.stream().filter(d -> d != null && !moveSourceContracts.classesToRemove.contains(d)).toList();
            unit.defs = List.from(list);
        }
        var refUpdater = new ReferenceUpdater();
        for(var unit : compilationUnits) {
            refUpdater.visitTopLevel(unit);
        }
        return compilationUnits;
    }

    class MoveSourceContracts extends TreeTranslator {

        Set<JCTree.JCClassDecl> classesToRemove = new HashSet<>();

        private Env<AttrContext> topLevelEnv;
        
        @Override
        public void visitTopLevel(JCTree.JCCompilationUnit tree) {
            topLevelEnv = enter.getTopLevelEnv(tree);
            reporter.compilationUnit = tree;
            super.visitTopLevel(tree);
        }

        @Override
        public void visitClassDef(JCTree.JCClassDecl classDecl) {
            var classAnnotationsByName = JavaToDafnyCompiler.getAnnotationsByName(classDecl.getModifiers());
            var contractAnnotation = classAnnotationsByName.get(Contract.class.getName());
            if (contractAnnotation != null) {
                var contracteeSymbol = getContractTarget(classDecl, contractAnnotation);
                if (contracteeSymbol == null) {
                    reporter.reportError(classDecl, "noContractTarget", classDecl.name.toString());
                    classesToRemove.add(classDecl);
                    return;
                }
                
                if (!symbolsWithContracts.add(contracteeSymbol)) {
                    reporter.reportError(contractAnnotation, "duplicateContract", contracteeSymbol.name);
                    return;
                }

                com.sun.tools.javac.util.List<Symbol.TypeVariableSymbol> typeParameters = contracteeSymbol.getTypeParameters();
                if (typeParameters.size() != classDecl.sym.getTypeParameters().size()) {
                    reporter.reportError(classDecl, "contractDifferentTypeParameters", classDecl.name.toString(), contracteeSymbol.name.toString());
                    return;
                }
        
                for (int i = 0; i < typeParameters.size(); i++) {
                    var originalTypeParameter = typeParameters.get(i);
                    var contractTypeParameter = classDecl.sym.getTypeParameters().get(i);
                    if (!originalTypeParameter.name.contentEquals(contractTypeParameter.name)) {
                        reporter.reportError(classDecl, "contractDifferentTypeParameters", classDecl.name.toString(), contracteeSymbol.name.toString());
                        return;
                    }
                }
                contractSymbolToContractee.put(classDecl.sym, contracteeSymbol);
                
                var contracteeSource = (JCTree.JCClassDecl)index.getTree(contracteeSymbol);
                if (contracteeSource == null) {
                    handleLibraryContract(classDecl, contracteeSymbol);
                } else {
                    if (JavaToDafnyCompiler.typeHasSource(index, contracteeSymbol) && !JavaToDafnyCompiler.isInterfaceOrAbstract(contracteeSymbol)) {
                        reporter.reportError(contractAnnotation, "concreteTypeWithExternalContract", contracteeSymbol.name);
                        return;
                    }
                    handleSourceContract(classDecl, classAnnotationsByName, contracteeSource, contracteeSymbol);
                }
            }
            
            super.visitClassDef(classDecl);
        }

        private void handleSourceContract(JCTree.JCClassDecl classDecl, Map<String, JCTree.JCAnnotation> classAnnotationsByName, JCTree.JCClassDecl contracteeSource, Symbol.ClassSymbol contracteeSymbol) {
            var modifiableAnnotation = classAnnotationsByName.get(Modifiable.class.getName());
            if (modifiableAnnotation != null) {
                reporter.reportError(modifiableAnnotation, "annotationOnSourceContractClass", 
                        Modifiable.class.getSimpleName(), classDecl.name.toString());
            }

            var contractAnnotation = classDecl.sym.getAnnotation(Contract.class);
            if (contractAnnotation != null && contractAnnotation.immutable()) {
                reporter.reportError(classDecl, "immutableInternalContract");
            }

            classesToRemove.add(classDecl);
            for(var member : classDecl.getMembers()) {
                if (member instanceof JCTree.JCVariableDecl field) {
                    contracteeSource.defs = contracteeSource.defs.append(field);
                    contracteeSource.sym.members().enter(field.sym);
                } else if (member instanceof JCTree.JCMethodDecl methodDecl) {
                    var methodSymbol = methodDecl.sym;
                    if (methodSymbol.isConstructor()) {
                        // source contract constructor are only to please the Java compiler.
                        continue;
                    }
                    
                    // TODO can the next line be removed?
                    if (JavaToDafnyCompiler.isSynthetic(index, methodDecl, methodSymbol) || (methodDecl.mods.flags & Flags.GENERATEDCONSTR) != 0) {
                        continue;
                    }
                    
                    var baseMethod = OverrideFinder.findOverriddenMethod(contracteeSymbol, methodSymbol, types);
                    if (baseMethod != null) {
                        var baseSource = (JCTree.JCMethodDecl)index.getTree(baseMethod);
                        if (baseSource.getBody() != null) {
                            reporter.reportError(methodDecl, "internalAndExternalContractForMethod", methodSymbol.name.toString());
                        } else {
                            baseSource.body = methodDecl.body;
                            baseSource.mods.annotations = methodDecl.mods.annotations;
                            baseSource.mods.annotations = baseSource.mods.annotations.append(getVerifyFalseAnnotation());
                        }
                    } else {
                        reporter.reportError(methodDecl, "unusedContractMethod", methodToString(methodDecl));
                    }
                }
            }
        }

        private void handleLibraryContract(JCTree.JCClassDecl classDecl, Symbol.ClassSymbol contracteeSymbol) {
            classDecl.type.tsym = contracteeSymbol; // Required before calling 'findOverriddenMethod'
            
            var newMembers =  new ArrayList<JCTree>();
            for(var member : classDecl.getMembers()) {
                if (member instanceof JCTree.JCMethodDecl methodDecl) {
                    var methodSymbol = methodDecl.sym;
    
                    if (JavaToDafnyCompiler.isSynthetic(index, methodDecl, methodSymbol) || (methodDecl.mods.flags & Flags.GENERATEDCONSTR) != 0) {
                        continue;
                    }
                    
                    var baseMethod = OverrideFinder.findOverriddenMethod(contracteeSymbol, methodSymbol, types);
                    if (baseMethod != null) {
                        newMembers.add(methodDecl);
                        methodDecl.sym = baseMethod;
                        contractSymbolToContractee.put(methodDecl.sym, baseMethod);
                    } else {
                        reporter.reportError(methodDecl, "unusedContractMethod", methodToString(methodDecl));
                    }
                } else if (member instanceof JCTree.JCVariableDecl field) {
                    var baseField = contracteeSymbol.members().getSymbolsByName(field.name, s -> s.getKind() == ElementKind.FIELD).iterator();
                    if (baseField.hasNext()) {
                        Symbol.VarSymbol contractedFieldSymbol = (Symbol.VarSymbol) baseField.next();
                        contractSymbolToContractee.put(field.sym, contractedFieldSymbol);
                        field.sym = contractedFieldSymbol;
                    }
                    newMembers.add(member);
                } else {
                    newMembers.add(member);
                }
                
            }
            classDecl.defs = List.from(newMembers);

            var oldSymbol = classDecl.sym;
            oldSymbol.name = contracteeSymbol.name;
            classDecl.sym = contracteeSymbol;
            classDecl.type = contracteeSymbol.type;

            classDecl.mods.annotations = classDecl.mods.annotations.append(getVerifyFalseAnnotation());

            // Moves the contract attribute, including its immutable part.
            ListBuffer<Attribute.Compound> newAnnotations = new ListBuffer<>();
            newAnnotations.addAll(contracteeSymbol.getAnnotationMirrors());
            newAnnotations.addAll(oldSymbol.getAnnotationMirrors());
            
            contracteeSymbol.clearAnnotationMetadata();
            contracteeSymbol.setDeclarationAttributes(newAnnotations.toList());

            index.put(classDecl.sym, enter.classEnv(classDecl, topLevelEnv));
        }

        public Symbol.ClassSymbol getContractTarget(JCTree.JCClassDecl classDecl,
                                                    JCTree.JCAnnotation contractAnnotation) {
            if (contractAnnotation == null) {
                return null;
            }

            var arguments = JavaToDafnyCompiler.getArguments(contractAnnotation);
            var symbol = JavaToDafnyCompiler.getClassSymbol(names,  arguments.get("value"));
            if (symbol == null || symbol.getQualifiedName().contentEquals("com.aws.jverify.Contract")) {
                var superClass = classDecl.sym.getSuperclass();
                if (classDecl.extending != null && superClass != null) {
                    return (Symbol.ClassSymbol) superClass.tsym;
                }
                var interfaces = classDecl.sym.getInterfaces();
                if (interfaces.isEmpty()) {
                    return null;
                }
                return (Symbol.ClassSymbol) interfaces.getFirst().tsym;
            }
            return symbol;
        }
    }

    private JCTree.JCAnnotation getVerifyFalseAnnotation() {
        var verifySymbol = elements.getTypeElement(Verify.class.getCanonicalName());
        return maker.Annotation(maker.Ident(verifySymbol), List.of(
                maker.Assign(maker.Ident(names.fromString("value")), maker.Literal(false))));
    }

    private String methodToString(JCTree tree) {
        if (tree instanceof JCTree.JCMethodDecl methodDecl){
            if (isConstructor(methodDecl.sym)) {
                return "constructor";
            } else {
                return "method '" + methodDecl.name + "'";
            }
        } else {
            return "lambda";
        }
    }

    class ReferenceUpdater extends TreeTranslator {
        @Override
        public void visitIdent(JCTree.JCIdent tree) {
            var updatedSymbol = contractSymbolToContractee.get(tree.sym);
            if (updatedSymbol != null) {
                tree.sym = updatedSymbol;
            }
            super.visitIdent(tree);
        }

        @Override
        public void visitSelect(JCTree.JCFieldAccess tree) {
            var updatedSymbol = contractSymbolToContractee.get(tree.sym);
            if (updatedSymbol != null) {
                tree.sym = updatedSymbol;
            }
            super.visitSelect(tree);
        }

        @Override
        public void visitVarDef(JCTree.JCVariableDecl tree) {
            updateOwner(tree.sym);
            if (tree.type != null) {
                var updatedSymbol = (Symbol.ClassSymbol)contractSymbolToContractee.get(tree.type.tsym);
                if (updatedSymbol != null) {
                    tree.type.tsym = updatedSymbol;
                }
            }
            super.visitVarDef(tree);
        }
        
        void updateOwner(Symbol symbol) {
            var updatedSymbol = contractSymbolToContractee.get(symbol.owner);
            if (updatedSymbol != null) {
                symbol.owner = updatedSymbol;
            }
        }

        @Override
        public void visitMethodDef(JCTree.JCMethodDecl tree) {
            updateOwner(tree.sym);
            super.visitMethodDef(tree);
        }
    }
}
