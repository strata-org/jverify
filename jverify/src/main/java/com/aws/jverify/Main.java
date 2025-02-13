package com.aws.jverify;

import com.aws.jverify.generated.*;

import javax.tools.JavaFileObject;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


public class Main {

    public static void main(String[] args) throws IOException {
        
        String javaCode = """
package example;

import static com.aws.jverify.JVerify.*;

class Main {
  static void Foo() {
    check(false);
  }
}
""";

        Writer writer = new OutputStreamWriter(System.out);
        var exitCode = verifyJavaExample(javaCode, writer);
        System.out.println("Process exited with code: " + exitCode);
    }

    public static int verifyJavaExample(String javaCode, Writer output) throws IOException {
        var libraryLocation = "/Users/rwillems/SourceCode/GradleBased/jverify/src/main/java/com/aws/jverify/JVerify.java";
        String library = Files.readString(Path.of(libraryLocation));

        List<JavaFileObject> files = List.of(new SourceFile("test.java", javaCode), new SourceFile(libraryLocation, library));

        var dafnyEquivalent = new JavaASTAnalyzer().analyzeJavaCode(files);
        var sb = new StringBuilder();
        new Serializer(new TextEncoder(sb)).serialize(dafnyEquivalent);
        var program = sb.toString();
        var dafny = "/Users/rwillems/SourceCode/dafny/Scripts/dafny";
        return process(dafny, program, output);
    }


    public static int process(String dafnyPath, String program, Writer output) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    dafnyPath,  // Program path
                    "verify",                       // First argument
                    "--input-format",
                    "Binary",
                    "--stdin"                        // Second argument
            );

            Process process = processBuilder.start();

            var writer = new OutputStreamWriter(process.getOutputStream());
            writer.write(program);
            writer.close();

            // Read the output
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                reader.transferTo(output);
            }

            int exitCode = process.waitFor();
            return exitCode;

        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    //    import JSpec.*;
//
//    class Empty {
//        static void Foo() {
//            assert(false);
//        }
//    }
    private static FileModuleDefinition getExampleDafnyProgram() {
        var token = new Token(0,1);
        var origin = new SourceOrigin(token, token, token);
        var name = new Name(origin, "foo");
        var trueLiteral = new LiteralExpr(origin, false);
        var assertStatement = new AssertStmt(origin, null, trueLiteral, null);
        var body = new BlockStmt(origin, null, List.of(assertStatement));
        var fooMethod = new Method(origin, name, null, true, false, List.of(), List.of(), List.of(),
                List.of(), new Specification<FrameExpression>(origin, List.of(), null),
                new Specification<Expression>(origin, List.of(), null),
                List.of(), new Specification<FrameExpression>(origin, List.of(), null), body, null, false);
        var emptyClass = new ClassDecl(origin, name, null, List.of(), List.of(fooMethod), List.of(), false);
        var moduleDef = new FileModuleDefinition(origin, name, List.of(), ModuleKindEnum.Concrete, null,
                null, List.of(emptyClass));
        return moduleDef;
    }
}
