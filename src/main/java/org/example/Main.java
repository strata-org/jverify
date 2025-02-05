package org.example;

import org.example.generated.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;

public class Main {
    public static void main(String[] args) {

        var program = getExampleDafnyProgram();
        var dafny = "/Users/rwillems/SourceCode/dafny/Scripts/dafny";

        var output = Serializer.serialize(program);
        process(dafny, output);
    }

    public static void process(String dafnyPath, String program) {
        try {
            // Create ProcessBuilder with command and arguments
            ProcessBuilder processBuilder = new ProcessBuilder(
                    dafnyPath,  // Program path
                    "verify",                       // First argument
                    "--input-format",
                    "Binary",
                    "--stdin"                        // Second argument
            );

            // Start the process
            Process process = processBuilder.start();

            var writer = new OutputStreamWriter(process.getOutputStream());
            writer.write(program);
            writer.close();

            // Read the output
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            // Wait for the process to complete
            int exitCode = process.waitFor();
            System.out.println("Process exited with code: " + exitCode);

        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
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
