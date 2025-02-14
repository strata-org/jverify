package com.aws.jverify;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {
        Writer writer = new OutputStreamWriter(System.out);
        var fileLocation = List.of(Path.of(args[0]));
        var exitCode = Driver.verifyJavaPaths(fileLocation, writer);
        writer.flush();
        System.exit(exitCode);
    }
}
