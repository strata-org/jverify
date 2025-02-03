package org.example;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.example.generated.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {

        String input = Files.readString(Path.of("/Users/rwillems/SourceCode/dafny/Source/Scripts/bin/Debug/net8.0/parsedAst.cs"));
        new CSharpToJavaConverter("org.example.generated").
                writeJava(input, Paths.get("/Users/rwillems/SourceCode/GradleBased/src/main/java/org/example/generated"));

        var program = getExampleDafnyProgram();

        var output = Serializer.serialize(program);
        System.out.println(output);
    }

    private static FileModuleDefinition getExampleDafnyProgram() {
        var token = new Token(0,0);
        var origin = new SourceOrigin(token, token, token);
        var name = new Name(origin, "foo");
        var trueLiteral = new LiteralExpr(origin, true);
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

//    import JSpec.*;
//
//    class Empty {
//        static void Foo() {
//            assert(false);
//        }
//    }
}

