package com.aws.jverify.verifier.compiler;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import java.util.*;

import static com.sun.tools.javac.code.Flags.*;

public class LambdaToAnonymousClassCompiler extends TreeTranslator {

    private final JCCompilationUnit compilationUnit;
    private final TreeMaker make;
    private final Names names;
    private final Types types;
    private final Context context;

    public LambdaToAnonymousClassCompiler(JCCompilationUnit compilationUnit, Context context) {
        this.compilationUnit = compilationUnit;
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
        var classSymbol = getClassSymbol(lambda);
        var implMethod = createImplementationMethod(classSymbol, lambda);
        var constructor = createConstructor(classSymbol);
        var classDef = createLocalClassDef(lambda, classSymbol, constructor, implMethod);

        return getNewClassExpression(lambda, classDef, constructor);
    }

    private JCNewClass getNewClassExpression(JCLambda lambda,
                                             JCClassDecl classDef,
                                             JCMethodDecl constructor) {
        var result = make.NewClass(
                null,
                List.nil(),
                make.Type(lambda.type),
                List.nil(),
                classDef
        );
        result.type = classDef.type;
        result.constructor = constructor.sym;
        return result;
    }

    private Symbol.ClassSymbol getClassSymbol(JCLambda lambda) {
        var line = compilationUnit.getLineMap().getLineNumber(lambda.pos);
        var column = compilationUnit.getLineMap().getColumnNumber(lambda.pos);
        Name name = names.lambda.append(names.fromString(line + "_" + column));

        int flags = SYNTHETIC | FINAL;
        boolean hasNoEnclosingType = (currentContainer.flags() & STATIC) != 0;
        if (hasNoEnclosingType) {
            flags |= STATIC;

        }
        var classSymbol = new Symbol.ClassSymbol(flags, name, currentContainer);

        // Flatname should be globally unique. Qualified class name plus line and column achieves that.
        classSymbol.flatname = currentContainer.owner.flatName().append(name);
        Type enclosingType = hasNoEnclosingType ? Type.noType : currentContainer.enclClass().type;
        Type.ClassType classType = new Type.ClassType(enclosingType, List.nil(), classSymbol);
        classType.interfaces_field = List.of(lambda.type);
        classSymbol.type = classType;
        classSymbol.members_field = Scope.WriteableScope.create(classSymbol);
        return classSymbol;
    }

    private JCClassDecl createLocalClassDef(JCLambda lambda, 
                                            Symbol.ClassSymbol classSymbol,  
                                            JCMethodDecl constructor,
                                            JCMethodDecl implMethod) {
        java.util.List<JCTree> classBody = new ArrayList<>();
        classBody.add(constructor);
        classBody.add(implMethod);
        classSymbol.members().enter(implMethod.sym);
        classSymbol.members().enter(constructor.sym);

        var classDef = make.ClassDef(
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

    private JCMethodDecl createConstructor(Symbol.ClassSymbol classSymbol) {
        var modifiers = make.Modifiers(SYNTHETIC);

        var methodSymbol = new Symbol.MethodSymbol(modifiers.flags, names.init, new Type.MethodType(
                List.nil(),
                classSymbol.type,
                List.nil(), classSymbol
        ), classSymbol);
        methodSymbol.params = List.nil();

        var result = make.MethodDef(
                modifiers,
                names.init,
                null,
                List.nil(),
                List.nil(),
                List.nil(),
                make.Block(0, List.nil()),
                null
        );
        result.sym = methodSymbol;
        return result;
    }

    private JCMethodDecl createImplementationMethod(Symbol.ClassSymbol classSymbol, JCLambda lambda) {

        var samMethod = (Symbol.MethodSymbol)types.findDescriptorSymbol(lambda.type.tsym);
        var methodType = types.memberType(lambda.type, samMethod);

        var modifiers = make.Modifiers(Flags.PUBLIC | SYNTHETIC);
        var methodSymbol = new Symbol.MethodSymbol(modifiers.flags, samMethod.name, new Type.MethodType(
                methodType.getParameterTypes(),
                methodType.getReturnType(),
                List.nil(), classSymbol
        ), classSymbol);
        methodSymbol.params = lambda.params.map(d -> d.sym);

        var methodBody = getMethodBody(lambda, methodSymbol);
        var result = make.MethodDef(
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
        qualifyThisAndUpdateOwners(methodSymbol, lambda);
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
    
    private void qualifyThisAndUpdateOwners(Symbol.MethodSymbol methodSymbol, JCLambda lambda) {

        TreeTranslator bodyTransformer = new TreeTranslator() {

            @Override
            public void visitClassDef(JCClassDecl tree) {
                result = tree;
            }

            @Override
            public void visitVarDef(JCVariableDecl tree) {
                // Variables declared in a lambda are owned by the containing method, 
                // but now they'll be owned by the impl method in the local class
                if (tree.sym.owner == currentContainer) {
                    tree.sym.owner = methodSymbol;
                }
                super.visitVarDef(tree);
            }

            @Override
            public void visitIdent(JCIdent ident) {
                // "this" suddenly refers to the local class, so we need to qualify it with the outer class.
                if (ident.name == names._this) {
                    make.pos = ident.pos;
                    Symbol.TypeSymbol thisClass = ident.sym.type.tsym;
                    JCIdent outerClass = make.Ident(thisClass.name);
                    outerClass.sym = thisClass;
                    outerClass.type = outerClass.sym.type;
                    JCFieldAccess select = make.Select(outerClass, names._this);
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