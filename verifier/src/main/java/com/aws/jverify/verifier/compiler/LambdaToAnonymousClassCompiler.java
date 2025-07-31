package com.aws.jverify.verifier.compiler;

import com.aws.jverify.verifier.compiler.simplifications.NameCompiler;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import java.util.*;

import static com.sun.tools.javac.code.Flags.FINAL;
import static com.sun.tools.javac.code.Flags.SYNTHETIC;

public class LambdaToAnonymousClassCompiler extends TreeTranslator {

    private final TreeMaker make;
    private final Names names;
    private final Types types;
    private final Context context;

    public LambdaToAnonymousClassCompiler(Context context) {
        this.make = TreeMaker.instance(context);
        this.names = Names.instance(context);
        this.types = Types.instance(context);
        this.context = context;
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation invocation) {
        var jverifyMethod = JavaToDafnyCompiler.getJVerifyMethod(invocation);
        if (jverifyMethod == null) {
            super.visitApply(invocation);
        } else {
            result = invocation;
        }
    }

    JCMethodDecl currentMethod;
    @Override
    public void visitMethodDef(JCMethodDecl tree) {
        currentMethod = tree;
        super.visitMethodDef(tree);
    }

    @Override
    public void visitLambda(JCLambda lambda) {
        new QualifyLocalMethodReferences(context).translate(lambda);
        super.visitLambda(lambda);
        result = transformLambdaToAnonymousClass(lambda);
    }

    private JCNewClass transformLambdaToAnonymousClass(JCLambda lambda) {
        Map<Symbol, JCExpression> captures = new HashMap<>();
        var classSymbol = getClassSymbol(lambda);

        JCMethodDecl implMethod = createImplementationMethod(classSymbol, lambda, captures);
        var constructor = createConstructor(classSymbol, captures);
        var classDef = createLocalClassDef(lambda, classSymbol, constructor, implMethod, captures);

        return getNewClassExpression(lambda, captures,  classDef, constructor);
    }

    private JCNewClass getNewClassExpression(JCLambda lambda,
                                             Map<Symbol, JCExpression> captures, 
                                             JCClassDecl classDef,
                                             JCMethodDecl constructor) {
        var result = make.NewClass(
                null,
                List.nil(),
                make.Type(lambda.type),
                List.from(captures.values()),
                classDef
        );
        result.type = classDef.type;
        result.constructor = constructor.sym;
        return result;
    }

    private Symbol.ClassSymbol getClassSymbol(JCLambda lambda) {
        var functionalInterfaceType = lambda.type;
        
        // TODO test for collisions
        Name append = names.lambda.append(names.fromString(lambda.pos + ""));
        
        var classSymbol = new Symbol.ClassSymbol(SYNTHETIC | FINAL, append, currentMethod.sym);
        Type.ClassType classType = new Type.ClassType(currentMethod.sym.enclClass().type, List.nil(), classSymbol);
        classType.interfaces_field = List.of(functionalInterfaceType);
        classSymbol.type = classType;
        classSymbol.members_field = Scope.WriteableScope.create(classSymbol);
        return classSymbol;
    }

    private JCClassDecl createLocalClassDef(JCLambda lambda, 
                                            Symbol.ClassSymbol classSymbol,  
                                            JCMethodDecl constructor,
                                            JCMethodDecl implMethod,
                                            Map<Symbol, JCExpression> captures) {

        
        var capturedFields = createCapturedFields(classSymbol, captures);

        java.util.List<JCTree> classBody = new ArrayList<>(capturedFields);
        classBody.add(constructor);
        classBody.add(implMethod);
        classSymbol.members().enter(implMethod.sym);
        classSymbol.members().enter(constructor.sym);

        JCClassDecl classDef = make.ClassDef(
                make.Modifiers(classSymbol.flags()),
                classSymbol.name,
                List.nil(),
                null,
                List.of(make.Type(lambda.type)),
                List.from(classBody)
        );
        classDef.sym = classSymbol;
        classDef.type = classSymbol.type;
        return classDef;
    }

    private List<JCVariableDecl> createCapturedParameters(Symbol.ClassSymbol classSymbol,
                                                          Map<Symbol, JCExpression> captures) {
        java.util.List<JCVariableDecl> params = new ArrayList<>();

        for (Symbol sym : captures.keySet()) {
            JCVariableDecl param = make.VarDef(
                    make.Modifiers(Flags.FINAL),
                    sym.name,
                    make.Type(sym.type),
                    null
            );
            param.type = sym.type;
            param.sym = new Symbol.VarSymbol(0, param.name, param.type, classSymbol);
            params.add(param);
        }

        return List.from(params);
    }

    private List<JCVariableDecl> createCapturedFields(Symbol.ClassSymbol classSymbol, Map<Symbol, JCExpression> lambda) {
        java.util.List<JCVariableDecl> fields = new ArrayList<>();

        for (Symbol sym : lambda.keySet()) {
            var name = sym.name;
            if (name == sym.name.table.names._this) {
                name = sym.name.table.names.fromString("captured" + NameCompiler.sep + "this");
            }
            
            JCVariableDecl field = make.VarDef(
                    make.Modifiers(Flags.PRIVATE | Flags.FINAL),
                    name,
                    make.Type(sym.type),
                    null
            );
            field.type = sym.type;
            field.sym = new Symbol.VarSymbol(FINAL, field.name, field.type, classSymbol);
            fields.add(field);
            classSymbol.members().enter(field.sym);
        }

        return List.from(fields);
    }

    private JCMethodDecl createConstructor(Symbol.ClassSymbol classSymbol, Map<Symbol, JCExpression> captures) {
        JCModifiers modifiers = make.Modifiers(SYNTHETIC);

        var capturedParams = createCapturedParameters(classSymbol, captures);
        Symbol.MethodSymbol methodSymbol = new Symbol.MethodSymbol(modifiers.flags, names.init, new Type.MethodType(
                capturedParams.map(vd -> vd.type),
                classSymbol.type,
                List.nil(), classSymbol
        ), classSymbol);
        methodSymbol.params = capturedParams.map(d -> new Symbol.VarSymbol(0, d.name, d.type, methodSymbol));
        
        java.util.List<JCStatement> assignments = new ArrayList<>();
        Symbol.VarSymbol thisSymbol = new Symbol.VarSymbol(FINAL, names._this, classSymbol.type, classSymbol);
        for (JCVariableDecl param : capturedParams) {
            JCIdent thisIdent = make.Ident(names._this);
            thisIdent.sym = thisSymbol;
            thisIdent.type = classSymbol.type;
            Symbol.VarSymbol fieldSym = new Symbol.VarSymbol(FINAL, param.name, param.type, methodSymbol);
            JCFieldAccess fieldAccess = make.Select(thisIdent, param.name);
            fieldAccess.type = param.type;
            fieldAccess.sym = fieldSym;
            
            JCIdent paramIdent = make.Ident(param.name);
            paramIdent.type = param.type;
            paramIdent.sym = fieldSym;
            
            JCAssign assignment = make.Assign(fieldAccess, paramIdent);
            assignments.add(make.Exec(assignment));
        }

        JCBlock body = make.Block(0, List.from(assignments));

        JCMethodDecl result = make.MethodDef(
                modifiers,
                names.init,
                null,
                List.nil(),
                capturedParams,
                List.nil(),
                body,
                null
        );
        result.sym = methodSymbol;
        return result;
    }

    private JCMethodDecl createImplementationMethod(Symbol.ClassSymbol classSymbol, 
                                                    JCLambda lambda,
                                                    Map<Symbol, JCExpression> captures) {

        var samMethod = (Symbol.MethodSymbol)types.findDescriptorSymbol(lambda.type.tsym);
        var methodType = types.memberType(lambda.type, samMethod);
        
        JCModifiers modifiers = make.Modifiers(Flags.PUBLIC | SYNTHETIC);
        Symbol.MethodSymbol methodSymbol = new Symbol.MethodSymbol(modifiers.flags, samMethod.name, new Type.MethodType(
                methodType.getParameterTypes(),
                methodType.getReturnType(),
                List.nil(), classSymbol
        ), classSymbol);
        methodSymbol.params = lambda.params.map(d -> d.sym);

        JCBlock methodBody = getMethodBody(classSymbol, lambda, methodSymbol, captures);
        JCMethodDecl result = make.MethodDef(
                modifiers,
                methodSymbol.name,
                make.Type(methodType.getReturnType()),
                List.nil(),
                lambda.params,
                List.nil(),
                methodBody,
                null
        );
        result.sym = methodSymbol;

        for(var param : lambda.params) {
            param.sym.owner = result.sym;
        }
        return result;
    }

    private JCBlock getMethodBody(Symbol.ClassSymbol classSymbol, JCLambda lambda, Symbol.MethodSymbol methodSymbol,
                                  Map<Symbol, JCExpression> captures) {
        retargetCapturedVariables(classSymbol, methodSymbol, lambda, captures);
        return handleExpressionOrBlockBody(lambda);
    }

    private JCBlock handleExpressionOrBlockBody(JCLambda lambda) {
        JCBlock methodBody;
        if (lambda.getBodyKind() == JCLambda.BodyKind.EXPRESSION) {
            JCReturn returnStmt = make.Return((JCExpression) lambda.body);
            methodBody = make.Block(0, List.of(returnStmt));
        } else {
            methodBody = (JCBlock) lambda.body;
        }
        return methodBody;
    }private void retargetCapturedVariables(Symbol.ClassSymbol classSymbol,
                                            Symbol.MethodSymbol methodSymbol,
                                            JCLambda lambda,
                                            Map<Symbol, JCExpression> capturedMap) {
        var thisSymbol = new Symbol.VarSymbol(FINAL, names._this, classSymbol.type, classSymbol);
        Map<Symbol, Symbol.VarSymbol> symbolToFieldMap = new HashMap<>();

        // Collect all local variables declared in the same block as the lambda
        Set<Symbol> localVariables = new HashSet<>();
        collectLocalVariables(lambda, localVariables);

        TreeTranslator bodyTransformer = new TreeTranslator() {

            boolean insideContract = false;

            @Override
            public void visitApply(JCTree.JCMethodInvocation invocation) {
                var jverifyMethod = JavaToDafnyCompiler.getJVerifyMethod(invocation);
                insideContract = jverifyMethod != null;
                super.visitApply(invocation);
                insideContract = false;
            }

            @Override
            public void visitVarDef(JCVariableDecl tree) {
                // TODO maybe move to a separate body transformer, so we don't need insideContract.
                if (tree.sym.owner instanceof Symbol.MethodSymbol) {
                    tree.sym.owner = methodSymbol;
                }
                super.visitVarDef(tree);
            }

            @Override
            public void visitIdent(JCIdent ident) {

                // Check if this is a captured variable (from enclosing scope)
                if (!insideContract && ident.sym != null && isFromEnclosingScope(ident.sym, lambda, localVariables)) {
                    if (!(ident.sym instanceof Symbol.ClassSymbol || ident.sym instanceof Symbol.PackageSymbol) ||
                            ident.name == ident.name.table.names._this) {

                        var name = ident.name;
                        if (name == ident.sym.name.table.names._this) {
                            name = ident.sym.name.table.names.fromString("captured" + NameCompiler.sep + "this");
                        }

                        // Create field declaration if not already present
                        final var finalName = name;
                        Symbol.VarSymbol fieldSym = symbolToFieldMap.computeIfAbsent(ident.sym,
                                sym -> new Symbol.VarSymbol(FINAL, finalName, sym.type, classSymbol));

                        // Only add to captured map if this is the first time we see this symbol
                        if (!capturedMap.containsKey(fieldSym)) {
                            JCExpression constructorArg = make.Ident(ident.sym);
                            capturedMap.put(fieldSym, constructorArg);
                        }

                        // Replace captured variable reference with this.fieldName
                        make.pos = ident.pos;
                        JCIdent thisIdent = make.Ident(names._this);
                        thisIdent.sym = thisSymbol;
                        thisIdent.type = classSymbol.type;
                        JCFieldAccess select = make.Select(thisIdent, name);
                        select.sym = fieldSym;
                        select.type = ident.type;
                        result = select;
                        return;
                    }
                }

                result = ident;
            }
        };

        lambda.body = bodyTransformer.translate(lambda.body);
    }

    private void collectLocalVariables(JCLambda lambda, Set<Symbol> localVariables) {
        TreeTranslator collector = new TreeTranslator() {
            @Override
            public void visitVarDef(JCVariableDecl tree) {
                if (tree.sym != null && tree.sym.owner == currentMethod.sym) {
                    localVariables.add(tree.sym);
                }
                super.visitVarDef(tree);
            }
        };

        // We need to traverse the lambda body to find local variable declarations
        collector.translate(lambda.body);
    }

    private boolean isFromEnclosingScope(Symbol sym, JCLambda lambda, Set<Symbol> localVariables) {
        for (JCVariableDecl param : lambda.params) {
            if (param.sym == sym) {
                return false;
            }
        }

        return !localVariables.contains(sym);
    }

}