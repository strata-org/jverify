package com.aws.jverify.plugin;

import com.aws.jverify.JVerify;
import com.aws.jverify.common.Common;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.constantpool.ClassEntry;
import java.net.URI;
import java.util.List;

import java.lang.classfile.*;
        
@SuppressWarnings("preview")
public class TestPlugin {

    @Test
    public void testASTModification() throws IOException {
        // Create a source file to modify
        JavaFileObject source = JavaFileObjects.forSourceString(
                "com.example.ModifyMe",
                "package com.example;\n" +
                        """
import com.aws.jverify.*;
import static com.aws.jverify.JVerify.*;

public class ModifyMe {
  void Bar() {
    precondition(false);
    postcondition(false);
    invariant(false);
  }
}"""
        );
        
        var jVerifyClass = URI.create(Common.getJarLocationForClass(JVerify.class));
                
        Compilation compilation = Compiler.javac()
                .withProcessors(new JVerifyProcessor())
                .withClasspath(List.of(new File(jVerifyClass)))
                .compile(source);

        var g = compilation.generatedFiles();
        for(var file : g) {
            inspectConstantPool(file);
        }
        
        CompilationSubject.assertThat(compilation).succeeded();
    }

    public void inspectConstantPool(JavaFileObject fileObject) throws IOException {
        InputStream inputStream = fileObject.openInputStream();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        byte[] classBytes = outputStream.toByteArray();
        ClassModel cm = ClassFile.of().parse(classBytes);
        for(var entry : cm.constantPool()) {
            if (entry instanceof ClassEntry classEntry) {
                if (classEntry.asInternalName().equals("com/aws/jverify/JVerify")) {
                    Assertions.fail("No calls to JVerify allowed in class file");
                }
            }
        }
    }
}
