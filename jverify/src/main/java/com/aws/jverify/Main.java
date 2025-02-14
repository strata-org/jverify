package com.aws.jverify;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws IOException {
        Writer writer = new OutputStreamWriter(System.out);
        var javaCode = Files.readString(Path.of(args[0]));
        var exitCode = Driver.verifyJavaExample(javaCode, writer);
        writer.flush();
    }
}
