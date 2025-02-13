package com.aws.jverify;

import com.aws.jverify.generated.*;

import javax.tools.JavaFileObject;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


public class Main {

    public static void main(String[] args) throws IOException {
        Writer writer = new OutputStreamWriter(System.out);
        var javaCode = Files.readString(Path.of(args[0]));
        var exitCode = Driver.verifyJavaExample(javaCode, writer);
        System.out.println("Process exited with code: " + exitCode);
    }
}
