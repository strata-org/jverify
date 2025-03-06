package com.aws.jverify.common;

import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;

public class Common {
    public static final String JVERIFY_CLASS = com.aws.jverify.JVerify.class.getName();
    
    public static String getJarLocationForClass(Class<?> clazz) {
        try {
            ProtectionDomain protectionDomain = clazz.getProtectionDomain();
            CodeSource codeSource = protectionDomain.getCodeSource();

            if (codeSource != null) {
                URL location = codeSource.getLocation();
                return location.toString();
            } else {
                // For JDK classes, we might need a different approach
                String resourcePath = "/" + clazz.getName().replace('.', '/') + ".class";
                URL resource = clazz.getResource(resourcePath);

                if (resource != null) {
                    String path = resource.toString();

                    // If it's in a jar, the URL will contain "jar:file:"
                    if (path.startsWith("jar:file:")) {
                        // Extract the path to the jar file
                        path = path.substring(9, path.lastIndexOf('!'));
                        return "file:" + path;
                    }
                    return path;
                }
            }

            return "Cannot determine location";
        } catch (Exception e) {
            return "Error determining location: " + e.getMessage();
        }
    }
}