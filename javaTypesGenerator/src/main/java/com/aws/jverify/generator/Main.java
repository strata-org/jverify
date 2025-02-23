package com.aws.jverify.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) throws IOException {

        var dir = Paths.get("/Users/rwillems/SourceCode/GradleBased/jverify/src/main/java/com/aws/jverify/generated");
        if (!Files.exists(dir)) {
            Files.createDirectory(dir);
        }
        String input = Files.readString(Path.of("./dafny/Source/Scripts/Syntax.cs-schema"));
        new CSharpToJavaConverter("com.aws.jverify.generated").
                writeJava(input, dir);

    }

}

