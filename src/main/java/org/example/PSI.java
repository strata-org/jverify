//package org.example;
//
//import com.intellij.core.CoreApplicationEnvironment;
//import com.intellij.core.CoreProjectEnvironment;
//import com.intellij.openapi.util.text.StringUtil;
//import com.intellij.psi.*;
//import com.intellij.psi.impl.source.PsiFileImpl;
//import com.intellij.testFramework.LightVirtualFile;
//
//public class PSIDemo {
//    public static String addLogging(String sourceCode) {
//        // Set up PSI environment
//        CoreApplicationEnvironment appEnv = new CoreApplicationEnvironment(new CoreApplicationEnvironment.ApplicationEnvironmentBuilder());
//        CoreProjectEnvironment projEnv = new CoreProjectEnvironment(appEnv);
//
//        // Create PSI file from source
//        LightVirtualFile virtualFile = new LightVirtualFile("Test.java", sourceCode);
//        PsiFileImpl file = (PsiFileImpl) projEnv.getPsiManager()
//                .findFile(virtualFile);
//
//        // Process methods
//        file.accept(new JavaRecursiveElementVisitor() {
//            @Override
//            public void visitMethod(PsiMethod method) {
//                PsiElementFactory factory = JavaPsiFacade.getElementFactory(projEnv.getProject());
//                String logText = "System.out.println(\"Entering " + method.getName() + "\");";
//                PsiStatement logStatement = factory.createStatementFromText(logText, method);
//
//                PsiCodeBlock body = method.getBody();
//                if (body != null) {
//                    body.addBefore(logStatement, body.getFirstBodyElement());
//                }
//            }
//        });
//
//        return file.getText();
//    }
//
//    public static void main(String[] args) {
//        String input =
//                "class Test {\n" +
//                        "    void method1() {\n" +
//                        "        System.out.println(\"Hello\");\n" +
//                        "    }\n" +
//                        "}";
//
//        String modified = addLogging(input);
//        System.out.println(modified);
//    }
//}