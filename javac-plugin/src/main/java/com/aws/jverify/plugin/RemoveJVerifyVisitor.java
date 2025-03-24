package com.aws.jverify.plugin;

import com.aws.jverify.CheckPreconditionsAtRuntime;
import com.aws.jverify.PreconditionFailure;
import com.aws.jverify.common.Common;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

import java.util.Stack;
import java.util.stream.Collectors;

class RemoveJVerifyVisitor extends TreeScanner {

    private final Stack<JCTree.JCMethodDecl> methodStack = new Stack<>();
    private final TreeMaker maker;
    private Env<AttrContext> currentEnv;
    private final Context context;
    private boolean dynamicPreconditions;

    public RemoveJVerifyVisitor(Context context) {
        this.maker = TreeMaker.instance(context);
        this.context = context;
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl tree) {
        Env<AttrContext> prevEnv = currentEnv;

        if (tree.sym != null) {
            currentEnv = Enter.instance(context).getClassEnv(tree.sym);
        }

        super.visitClassDef(tree);

        currentEnv = prevEnv;
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        methodStack.push(tree);

        var currentMethod = methodStack.peek();

        var annotations = currentMethod.getModifiers().getAnnotations();
        var annotationsByName = annotations.stream().collect(Collectors.toMap(
                (JCTree.JCAnnotation a) -> a.getAnnotationType().toString(),
                a -> a));
        this.dynamicPreconditions = annotationsByName.containsKey(CheckPreconditionsAtRuntime.class.getSimpleName());
        
        super.visitMethodDef(tree);
        Attr attr = Attr.instance(context);
        attr.attribStat(tree, currentEnv);
        methodStack.pop();
    }
    
    @Override
    public void visitBlock(JCTree.JCBlock tree) {
        
        super.visitBlock(tree);
        tree.stats = getNewStatements(tree.stats);
    }

    private List<JCTree.JCStatement> getNewStatements(List<JCTree.JCStatement> statements) {
        var current = statements.head;
        if (!(current instanceof JCTree.JCExpressionStatement expressionStatement)) {
            return statements;
        }
        
        var expr = expressionStatement.getExpression();
        if (!(expr instanceof JCTree.JCMethodInvocation invocation)) {
            return statements;
        }
        
        var methodSymbol = (Symbol.MethodSymbol) TreeInfo.symbol(invocation.getMethodSelect());
        if (!fromJVerify(methodSymbol)) {
            return statements;
        }

        if (dynamicPreconditions && methodSymbol.getQualifiedName().contentEquals(Common.PRECONDITION)) {                            
            JCTree.JCBlock emptyBlock = maker.Block(0, List.nil());
            JCTree.JCExpression head = invocation.getArguments().head;

            var check = maker.If(maker.Unary(JCTree.Tag.NOT, head), maker.Block(0, List.of(
                    maker.Throw(maker.NewClass(null,null,
                            maker.QualIdent(getClassSymbol(PreconditionFailure.class, context)), 
                            List.nil(), null))
            )), emptyBlock);
            return statements.tail.prepend(check);
        } else {
            return statements.tail;
        }
    }

    public Symbol.ClassSymbol getClassSymbol(Class<?> clazz, Context context) {
        var elements = JavacElements.instance(context);
        return elements.getTypeElement(clazz.getCanonicalName());
    }
    
    private static boolean fromJVerify(Symbol.MethodSymbol methodSymbol) {
        return methodSymbol.getEnclosingElement().getQualifiedName().contentEquals(Common.JVERIFY_CLASS);
    }
}
