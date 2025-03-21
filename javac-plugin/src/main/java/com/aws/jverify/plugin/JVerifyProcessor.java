package com.aws.jverify.plugin;

import com.aws.jverify.CheckPreconditionDynamically;
import com.aws.jverify.PreconditionFailure;
import com.aws.jverify.common.Common;
import com.google.auto.service.AutoService;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.comp.Flow;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.TreeScanner;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import java.util.Collections;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class JVerifyProcessor extends AbstractProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.emptySet();
    }
    
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        JavacTask task = JavacTask.instance(processingEnv);

        task.addTaskListener(new TaskListener() {
            @Override
            public void finished(TaskEvent e) {
                boolean resolutionInformationBecameAvailable = e.getKind() == TaskEvent.Kind.ANALYZE;
                if (resolutionInformationBecameAvailable) {
                    JCTree.JCCompilationUnit cu = (JCTree.JCCompilationUnit) e.getCompilationUnit();

                    var visitor = new RemoveJVerifyVisitor(null);
                    for (var rootElement : cu.getTypeDecls()) {
                        if (rootElement instanceof JCTree.JCClassDecl classDecl) {
                            visitor.visitClassDef(classDecl);
                        }
                    }
                    
                }
            }
        });
    }
    
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return true;
    }    
}

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
            if (statement instanceof JCTree.JCExpressionStatement expressionStatement) {
                var expr = expressionStatement.getExpression();
                if (expr instanceof JCTree.JCMethodInvocation invocation) {
                    var methodSymbol = (Symbol.MethodSymbol) TreeInfo.symbol(invocation.getMethodSelect());
                    if (fromJVerify(methodSymbol)) {
                        var currentMethod = methodStack.peek();

                        var annotations = currentMethod.getModifiers().getAnnotations();
                        var annotationsByName = annotations.stream().collect(Collectors.toMap(
                                (JCTree.JCAnnotation a) -> a.getAnnotationType().toString(),
                                a -> a));
                        var dynamicPreconditions = annotationsByName.containsKey(CheckPreconditionDynamically.class.getSimpleName());
                        
                        if (dynamicPreconditions && methodSymbol.getQualifiedName().toString().equals("precondition")) {                            
                            JCTree.JCBlock emptyBlock = maker.Block(0, List.nil());
                            currentStats.tail.head = maker.If(invocation.getArguments().head, maker.Block(0, List.of(
                                    maker.Throw(maker.NewClass(null,null,
                                            maker.ClassLiteral(getClassSymbol(PreconditionFailure.class, context)), 
                                            List.nil(), null))
                            )), emptyBlock);

                            //flow.analyzeTree(currentEnv, currentMethod);
                        } else {
                            currentStats.tail = currentStats.tail.tail;
                            continue;
                        }
                    }
                }
            }
            currentStats = currentStats.tail;
        }
        tree.stats = outerStats.tail;
    }

    public Symbol.ClassSymbol getClassSymbol(Class<?> clazz, Context context) {
        var elements = JavacElements.instance(context);
        return elements.getTypeElement(clazz.getCanonicalName());
    }
    
    private static boolean fromJVerify(Symbol.MethodSymbol methodSymbol) {
        return methodSymbol.getEnclosingElement().getQualifiedName().contentEquals(Common.JVERIFY_CLASS);
    }
}