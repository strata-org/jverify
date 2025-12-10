package com.aws.jverify.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class Common {
    public static final String JVERIFY_CLASS = com.aws.jverify.JVerify.class.getName();

    public static final String PRECONDITION = "precondition";
    
    public static String getJarLocationForClass(Class<?> clazz) {
        var protectionDomain = clazz.getProtectionDomain();
        var codeSource = protectionDomain.getCodeSource();

        if (codeSource != null) {
            URL location = codeSource.getLocation();
            return location.toString();
        } else {
            throw new RuntimeException("Not supported");
        }
    }

    public static String getResourceFile(Class<?> clazz, String name) {
        InputStream stream = clazz.getResourceAsStream(name);
        if (stream == null) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().map(line -> line + "\n").collect(Collectors.joining());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + name + " resource", e);
        }
    }

    public static String getJarEntry(JarFile jarFile, JarEntry entry) {
        try (InputStream stream = jarFile.getInputStream(entry)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines().map(line -> line + "\n").collect(Collectors.joining());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getExtraS(int count) {
        return count != 1 ? "s" : "";
    }
}