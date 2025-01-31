package org.example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JType;
import org.jsonschema2pojo.*;
import org.jsonschema2pojo.rules.Rule;
import org.jsonschema2pojo.rules.RuleFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) throws IOException {

        var bufferedWriter = Files.newBufferedWriter(Paths.get("/Users/rwillems/SourceCode/GradleBased/out/ast.java"));
        String input = Files.readString(Path.of("/Users/rwillems/SourceCode/dafny/Source/Scripts/bin/Debug/net8.0/parsedAst.cs"));
        CSharpToJavaConverter.writeJava(input, bufferedWriter);

    }
}

