import com.sun.source.tree.*;
import com.sun.source.util.*;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;

public class JavaASTAnalyzer {
    static class SourceFile extends SimpleJavaFileObject {
        final String content;

        SourceFile(String content) {
            super(URI.create("string:///Test.java"), Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
    }

    public static void analyzeJavaCode(String sourceCode) {
        // Get the Java compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Context context = new Context();
        JavacFileManager fileManager = new JavacFileManager(context, true, null);

        // Create a file object from the source code string
        JavaFileObject file = new SourceFile(sourceCode);

        // Create a compilation task
        JavacTaskImpl task = (JavacTaskImpl) compiler.getTask(
                null,
                fileManager,
                null,
                null,
                null,
                Collections.singletonList(file)
        );

        try {
            // Parse the source file into an AST
            JCTree.JCCompilationUnit compilationUnit = task.parse().iterator().next();

            // Create a TreeScanner to visit the AST
            TreeScanner<Void, Void> visitor = new TreeScanner<>() {
                @Override
                public Void visitMethod(MethodTree node, Void unused) {
                    System.out.println("Found method: " + node.getName());
                    return super.visitMethod(node, unused);
                }

                @Override
                public Void visitVariable(VariableTree node, Void unused) {
                    System.out.println("Found variable: " + node.getName() +
                            " of type: " + node.getType());
                    return super.visitVariable(node, unused);
                }

                @Override
                public Void visitClass(ClassTree node, Void unused) {
                    System.out.println("Found class: " + node.getSimpleName());
                    return super.visitClass(node, unused);
                }
            };

            // Scan the compilation unit
            visitor.scan(compilationUnit, null);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Example usage
        String javaCode = """
            public class Example {
                private int x;
                
                public void doSomething() {
                    System.out.println("Hello!");
                }
            }
            """;

        analyzeJavaCode(javaCode);
    }
}