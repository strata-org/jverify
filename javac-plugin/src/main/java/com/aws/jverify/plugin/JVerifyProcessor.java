package com.aws.jverify.plugin;

import com.aws.jverify.common.Common;
import com.google.auto.service.AutoService;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
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

                    var visitor = new RemoveJVerifyVisitor();
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
                        currentStats.tail = currentStats.tail.tail;
                        continue;
                    }
                }
            }
            currentStats = currentStats.tail;
        }
        tree.stats = outerStats.tail;
    }
    
    private static boolean fromJVerify(Symbol.MethodSymbol methodSymbol) {
        return methodSymbol.getEnclosingElement().getQualifiedName().contentEquals(Common.JVERIFY_CLASS);
    }
}