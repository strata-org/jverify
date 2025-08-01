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

    Symbol currentContainer;
    @Override
    public void visitMethodDef(JCMethodDecl tree) {
        var previous = currentContainer;
        currentContainer = tree.sym;
        super.visitMethodDef(tree);
        currentContainer = previous;
    }

    @Override
    public void visitLambda(JCLambda lambda) {
//        new QualifyLocalMethodReferences(context).translate(lambda);
        JCNewClass localClass = transformLambdaToAnonymousClass(lambda);
        super.visitNewClass(localClass);
    }

    @Override
    public void visitClassDef(JCClassDecl tree) {
        var previous = currentContainer;
        currentContainer = tree.sym;
        super.visitClassDef(tree);
        currentContainer = previous;
    }

    private JCNewClass transformLambdaToAnonymousClass(JCLambda lambda) {
        Map<Symbol, CapturedData> captures = new HashMap<>();
        var classSymbol = getClassSymbol(lambda);

        JCMethodDecl implMethod = createImplementationMethod(classSymbol, lambda);
        var constructor = createConstructor(classSymbol, captures);
        var classDef = createLocalClassDef(lambda, classSymbol, constructor, implMethod, captures);

        return getNewClassExpression(lambda, captures,  classDef, constructor);
    }

    private JCNewClass getNewClassExpression(JCLambda lambda,
                                             Map<Symbol, CapturedData> captures, 
                                             JCClassDecl classDef,
                                             JCMethodDecl constructor) {
        var result = make.NewClass(
                null,
                List.nil(),
                make.Type(lambda.type),
                captures.values().stream().map(CapturedData::constructorArgument).collect(List.collector()),
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
        
        var classSymbol = new Symbol.ClassSymbol(SYNTHETIC | FINAL, append, currentContainer);
        Type.ClassType classType = new Type.ClassType(currentContainer.enclClass().type, List.nil(), classSymbol);
        classType.interfaces_field = List.of(functionalInterfaceType);
        classSymbol.type = classType;
        classSymbol.members_field = Scope.WriteableScope.create(classSymbol);
        return classSymbol;
    }

    private JCClassDecl createLocalClassDef(JCLambda lambda, 
                                            Symbol.ClassSymbol classSymbol,  
                                            JCMethodDecl constructor,
                                            JCMethodDecl implMethod,
                                            Map<Symbol, CapturedData> captures) {

        
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
                                                          Map<Symbol, CapturedData> captures) {
        java.util.List<JCVariableDecl> params = new ArrayList<>();

        for (var capture : captures.values()) {
            JCVariableDecl param = make.VarDef(
                    make.Modifiers(Flags.FINAL),
                    capture.fieldSymbol.name,
                    make.Type(capture.fieldSymbol.type),
                    null
            );
            param.type = capture.fieldSymbol.type;
            param.sym = new Symbol.VarSymbol(0, param.name, param.type, classSymbol);
            params.add(param);
        }

        return List.from(params);
    }

    private List<JCVariableDecl> createCapturedFields(Symbol.ClassSymbol classSymbol, Map<Symbol, CapturedData> captures) {
        java.util.List<JCVariableDecl> fields = new ArrayList<>();

        for (var capture : captures.values()) {

            JCVariableDecl field = make.VarDef(
                    make.Modifiers(Flags.PRIVATE | Flags.FINAL),
                    capture.fieldSymbol.name,
                    make.Type(capture.fieldSymbol.type),
                    null
            );
            field.type = capture.fieldSymbol.type;
            field.sym = capture.fieldSymbol;
            fields.add(field);
            classSymbol.members().enter(field.sym);
        }

        return List.from(fields);
    }

    private static Name getCapturedVariableName(Symbol sym) {
        var name = sym.name;
        if (name == sym.name.table.names._this) {
            name = sym.name.table.names.fromString("captured" + NameCompiler.sep + sym.name);
        }
        return name;
    }

    private JCMethodDecl createConstructor(Symbol.ClassSymbol classSymbol, Map<Symbol, CapturedData> captures) {
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

    private JCMethodDecl createImplementationMethod(Symbol.ClassSymbol classSymbol, JCLambda lambda) {

        var samMethod = (Symbol.MethodSymbol)types.findDescriptorSymbol(lambda.type.tsym);
        var methodType = types.memberType(lambda.type, samMethod);
        
        JCModifiers modifiers = make.Modifiers(Flags.PUBLIC | SYNTHETIC);
        Symbol.MethodSymbol methodSymbol = new Symbol.MethodSymbol(modifiers.flags, samMethod.name, new Type.MethodType(
                methodType.getParameterTypes(),
                methodType.getReturnType(),
                List.nil(), classSymbol
        ), classSymbol);
        methodSymbol.params = lambda.params.map(d -> d.sym);

        JCBlock methodBody = getMethodBody(lambda, methodSymbol);
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

    private JCBlock getMethodBody(JCLambda lambda, Symbol.MethodSymbol methodSymbol) {
        retargetCapturedVariables(methodSymbol, lambda);
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
    
    record CapturedData(Symbol.VarSymbol fieldSymbol, JCExpression constructorArgument) {}
    
    private void retargetCapturedVariables(Symbol.MethodSymbol methodSymbol, JCLambda lambda) {

        TreeTranslator bodyTransformer = new TreeTranslator() {

            @Override
            public void visitClassDef(JCClassDecl tree) {
                result = tree;
            }

            @Override
            public void visitVarDef(JCVariableDecl tree) {
                if (tree.sym.owner == currentContainer) {
                    tree.sym.owner = methodSymbol;
                }
                super.visitVarDef(tree);
            }

            @Override
            public void visitIdent(JCIdent ident) {
                if (ident.name == names._this) {
                    make.pos = ident.pos;
                    Symbol.TypeSymbol thisClass = ident.sym.type.tsym;
                    JCIdent thisIdent = make.Ident(thisClass.name);
                    thisIdent.sym = thisClass;
                    thisIdent.type = thisIdent.sym.type;
                    JCFieldAccess select = make.Select(thisIdent, names._this);
                    select.sym = thisClass;
                    select.type = select.sym.type;
                    result = select;
                    return;
                }
                super.visitIdent(ident);
            }
        };

        lambda.body = bodyTransformer.translate(lambda.body);
    }
}