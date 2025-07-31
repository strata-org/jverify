package com.aws.jverify.verifier.compiler;

import com.aws.jverify.verifier.compiler.simplifications.NameCompiler;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

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
        var classSymbol = getClassSymbol(lambda);
        
        var capturedArgs = createCapturedArguments(lambda); // Needs to occur before calling 'createLocalClassDef'
        var constructor = createConstructor(lambda, classSymbol);
        var classDef = createLocalClassDef(lambda, classSymbol, constructor);

        return getNewClassExpression(lambda, capturedArgs, classDef, constructor);
    }

    private JCNewClass getNewClassExpression(JCLambda lambda, 
                                             List<JCExpression> capturedArgs, 
                                             JCClassDecl classDef,
                                             JCMethodDecl constructor) {
        var result = make.NewClass(
                null,
                List.nil(),
                make.Type(lambda.type),
                capturedArgs,
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
                                            JCMethodDecl constructor) {

        var functionalMethod = (Symbol.MethodSymbol)types.findDescriptorSymbol(lambda.type.tsym);
        var resolvedMethodType = types.memberType(lambda.type, functionalMethod);
        
        var capturedFields = createCapturedFields(classSymbol, lambda); // Needs to be called before 'createImplementationMethod'

        // TODO: see if we can prevent changes the lambda body, since that is a source of confusion
        JCMethodDecl implMethod = createImplementationMethod(classSymbol, lambda, resolvedMethodType, functionalMethod);

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

    private List<JCVariableDecl> createCapturedParameters(JCLambda lambda, Symbol.ClassSymbol classSymbol) {
        java.util.List<JCVariableDecl> params = new ArrayList<>();
        Set<Symbol> captured = findCapturedVariables(lambda);

        for (Symbol sym : captured) {
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

    private List<JCExpression> createCapturedArguments(JCLambda lambda) {
        java.util.List<JCExpression> args = new ArrayList<>();
        Set<Symbol> captured = findCapturedVariables(lambda);

        for (Symbol sym : captured) {
            args.add(make.Ident(sym));
        }

        return List.from(args);
    }

    private List<JCVariableDecl> createCapturedFields(Symbol.ClassSymbol classSymbol, JCLambda lambda) {
        java.util.List<JCVariableDecl> fields = new ArrayList<>();
        Set<Symbol> captured = findCapturedVariables(lambda);

        for (Symbol sym : captured) {
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

    private JCMethodDecl createConstructor(JCLambda lambda, Symbol.ClassSymbol classSymbol) {
        JCModifiers modifiers = make.Modifiers(SYNTHETIC);

        var capturedParams = createCapturedParameters(lambda, classSymbol);
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
                                                    Type methodType,
                                                    Symbol.MethodSymbol samMethod) {

        JCModifiers modifiers = make.Modifiers(Flags.PUBLIC | SYNTHETIC);
        Symbol.MethodSymbol methodSymbol = new Symbol.MethodSymbol(modifiers.flags, samMethod.name, new Type.MethodType(
                methodType.getParameterTypes(),
                methodType.getReturnType(),
                List.nil(), classSymbol
        ), classSymbol);
        methodSymbol.params = lambda.params.map(d -> d.sym);

        JCBlock methodBody = getMethodBody(classSymbol, lambda, methodSymbol);
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

    private JCBlock getMethodBody(Symbol.ClassSymbol classSymbol, JCLambda lambda, Symbol.MethodSymbol methodSymbol) {
        retargetCapturedVariables(classSymbol, methodSymbol, lambda);
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
    }

    private void retargetCapturedVariables(Symbol.ClassSymbol classSymbol,
                                             Symbol.MethodSymbol methodSymbol,
                                             JCLambda lambda) {
        Set<Symbol> capturedVars = findCapturedVariables(lambda);
        var body = lambda.body;
        Symbol.VarSymbol thisSymbol = new Symbol.VarSymbol(FINAL, names._this, classSymbol.type, classSymbol);
        
        TreeTranslator bodyTransformer = new TreeTranslator() {
            @Override
            public void visitIdent(JCIdent ident) {
                var name = ident.name;
                if (name == ident.sym.name.table.names._this) {
                    name = ident.sym.name.table.names.fromString("captured" + NameCompiler.sep + "this");
                }
                
                if (capturedVars.contains(ident.sym)) {
                    // Replace captured variable reference with this.fieldName
                    JCIdent thisIdent = make.Ident(names._this);
                    thisIdent.sym = thisSymbol;
                    thisIdent.type = classSymbol.type;
                    JCFieldAccess select = make.Select(thisIdent, name);
                    select.sym = new Symbol.VarSymbol(FINAL, name,  ident.type, classSymbol);
                    select.type = ident.type;
                    result = select;
                } else {
                    if (ident.sym.owner instanceof Symbol.MethodSymbol) {
                        ident.sym.owner = methodSymbol;
                    }
                    result = ident;
                }
            }
        };

        lambda.body = bodyTransformer.translate(body);
    }

    private Set<Symbol> findCapturedVariables(JCLambda lambda) {
        Set<Symbol> captured = new HashSet<>();

        TreeScanner captureScanner = new TreeScanner() {

            @Override
            public void visitApply(JCTree.JCMethodInvocation invocation) {
                var jverifyMethod = JavaToDafnyCompiler.getJVerifyMethod(invocation);
                if (jverifyMethod == null) {
                    super.visitApply(invocation);
                }
            }
            
            @Override
            public void visitIdent(JCIdent ident) {
                Symbol sym = ident.sym;
                if (sym != null && isFromEnclosingScope(sym, lambda)) {
                    if (!(sym instanceof Symbol.ClassSymbol || sym instanceof Symbol.PackageSymbol) || ident.name == ident.name.table.names._this) {
                        captured.add(sym);
                    }
                }
                super.visitIdent(ident);
            }
        };

        captureScanner.scan(lambda.body);
        return captured;
    }

    private boolean isFromEnclosingScope(Symbol sym, JCLambda lambda) {
        for (JCVariableDecl param : lambda.params) {
            if (param.sym == sym) {
                return false;
            }
        }
        return true;
    }
}