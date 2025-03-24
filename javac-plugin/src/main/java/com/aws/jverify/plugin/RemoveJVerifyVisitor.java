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
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import java.util.Stack;
import java.util.stream.Collectors;

class RemoveJVerifyVisitor extends TreeScanner {

    private final Stack<JCTree.JCMethodDecl> methodStack = new Stack<>();
    private final TreeMaker maker;
    private final Flow flow;
    private Env<AttrContext> currentEnv;
    private final Enter enter;
    private final Context context;

    public RemoveJVerifyVisitor(Context context) {
        this.maker = TreeMaker.instance(context);
        this.flow = Flow.instance(context);
        this.enter = Enter.instance(context);
        this.context = context;
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl tree) {
        Env<AttrContext> prevEnv = currentEnv;

        if (tree.sym != null) {
            currentEnv = enter.getClassEnv(tree.sym);
        }

        super.visitClassDef(tree);

        currentEnv = prevEnv;
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        methodStack.push(tree);
        super.visitMethodDef(tree);
        Attr attr = Attr.instance(context);
        attr.attribStat(tree, currentEnv);
        methodStack.pop();
    }
    
    @Override
    public void visitBlock(JCTree.JCBlock tree) {
        super.visitBlock(tree);
        
        // The type of outerStats needs to be a pointer to a linked list
        // We can use a linked list cons for that, where we ignore the head.
        var outerStats = tree.stats.prepend(null);
        var currentStats = outerStats;
        while(currentStats.size() > 1) {
            var statement = currentStats.tail.head;
            if (handleCons(statement, currentStats)) {
                currentStats = currentStats.tail;
            }
        }
        tree.stats = outerStats.tail;
    }

    private boolean handleCons(JCTree.JCStatement statement, List<JCTree.JCStatement> currentStats) {
        if (!(statement instanceof JCTree.JCExpressionStatement expressionStatement)) {
            return true;
        }
        
        var expr = expressionStatement.getExpression();
        if (!(expr instanceof JCTree.JCMethodInvocation invocation)) {
            return true;
        }
        
        var methodSymbol = (Symbol.MethodSymbol) TreeInfo.symbol(invocation.getMethodSelect());
        if (!fromJVerify(methodSymbol)) {
            return true;
        }
        
        var currentMethod = methodStack.peek();

        var annotations = currentMethod.getModifiers().getAnnotations();
        var annotationsByName = annotations.stream().collect(Collectors.toMap(
                (JCTree.JCAnnotation a) -> a.getAnnotationType().toString(),
                a -> a));
        var dynamicPreconditions = annotationsByName.containsKey(CheckPreconditionsAtRuntime.class.getSimpleName());

        if (dynamicPreconditions && methodSymbol.getQualifiedName().toString().equals("precondition")) {                            
            JCTree.JCBlock emptyBlock = maker.Block(0, List.nil());
            JCTree.JCExpression head = invocation.getArguments().head;

            currentStats.tail.head = maker.If(maker.Unary(JCTree.Tag.NOT, head), maker.Block(0, List.of(
                    maker.Throw(maker.NewClass(null,null,
                            maker.QualIdent(getClassSymbol(PreconditionFailure.class, context)), 
                            List.nil(), null))
            )), emptyBlock);

        } else {
            currentStats.tail = currentStats.tail.tail;
            return false;
        }
        return true;
    }

    public Symbol.ClassSymbol getClassSymbol(Class<?> clazz, Context context) {
        var elements = JavacElements.instance(context);
        return elements.getTypeElement(clazz.getCanonicalName());
    }
    
    private static boolean fromJVerify(Symbol.MethodSymbol methodSymbol) {
        return methodSymbol.getEnclosingElement().getQualifiedName().contentEquals(Common.JVERIFY_CLASS);
    }
}
