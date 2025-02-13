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
        var exitCode = Driver.verifyJavaExample(javaCode, writer);
        System.out.println("Process exited with code: " + exitCode);
    }
}
