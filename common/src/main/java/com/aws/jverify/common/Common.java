package com.aws.jverify.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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

    /**
     * Gets all source files from a JAR file.
     *
     * @param jarPath The path to the JAR file
     * @return A map of source paths to their content
     */
    public static Map<String, String> getAllSourcesFromJar(String jarPath) throws IOException {
        Map<String, String> sources = new HashMap<>();

        try (JarFile jar = new JarFile(jarPath)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (!name.endsWith(".java") || entry.isDirectory()) {
                    continue;
                }
                
                try (var is = jar.getInputStream(entry);
                     var reader = new BufferedReader(new InputStreamReader(is))) {

                    var sourceBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sourceBuilder.append(line).append("\n");
                    }
                    sources.put(name, sourceBuilder.toString());
                }
            }
        }

        return sources;
    }

    /**
     * Gets all source files from the current ClassLoader by searching for a specific class's JAR.
     *
     * @param referenceClass A class from the JAR you want to search
     * @return A map of source paths to their content
     */
    public static Map<String, String> getAllSourcesFromClassJar(Class<?> referenceClass) throws IOException {
        var jarFile = getJarLocationForClass(referenceClass);
        if (jarFile == null) {
            return Map.of();
        }
        return getAllSourcesFromJar(jarFile);
    }

    public static String getResourceFile(Class<?> clazz, String name) {
        InputStream stream = clazz.getResourceAsStream(name);
        if (stream == null) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read contracts.java resource", e);
        }
    }
}