package com.aws.jverify.plugin;

import com.google.auto.service.AutoService;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import java.util.Collections;
import java.util.Set;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class JVerifyProcessor extends AbstractProcessor {
    private Trees trees;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        // Process all elements
        return Collections.emptySet();
    }
    
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        JavacTask task = JavacTask.instance(processingEnv);

        task.addTaskListener(new TaskListener() {
            @Override
            public void finished(TaskEvent e) {
                if (e.getKind() == TaskEvent.Kind.ANALYZE) {
                    JCTree.JCCompilationUnit cu = (JCTree.JCCompilationUnit) e.getCompilationUnit();

                    var visitor = new RemoveJVerifyVisitor();
                    for (var rootElement : cu.getTypeDecls()) {
                        if (rootElement instanceof JCTree.JCClassDecl classDecl) {
                            visitor.visitClassDef(classDecl);
                        }
                    }
                    
                }
            }
        });

        
        this.trees = Trees.instance(processingEnv);
    }
    
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return true;
    }    
}

class RemoveJVerifyVisitor extends TreeScanner {
    @Override
    public void visitBlock(JCTree.JCBlock tree) {
        super.visitBlock(tree);
        var outerStats = tree.stats.prepend(null);
        var currentStats = outerStats;
        while(currentStats.size() > 1) {
            var statement = currentStats.tail.head;
            if (statement instanceof JCTree.JCExpressionStatement expressionStatement) {
                var expr = expressionStatement.getExpression();
                if (expr instanceof JCTree.JCMethodInvocation invocation) {
                    var methodSymbol = (Symbol.MethodSymbol) TreeInfo.symbol(invocation.getMethodSelect());
                    if (fromJVerify(methodSymbol)) {
                        currentStats.tail = currentStats.tail.tail;
                        continue;
                    }
                }
            }
            currentStats = currentStats.tail;
        }
        tree.stats = outerStats.tail;
    }
    
    // TODO deduplicate
    private static boolean fromJVerify(Symbol.MethodSymbol methodSymbol) {
        return methodSymbol.getEnclosingElement().getQualifiedName().contentEquals(JVERIFY_CLASS);
    }
    // TODO deduplicate
    public static final String JVERIFY_CLASS = com.aws.jverify.JVerify.class.getName();
}