package org.strata.java.bytecode.unfolding;

import com.sun.source.util.JavacTask;

import java.util.ArrayList;
import java.util.List;

/**
 * The javac packages {@link ConstantUnfolding} uses, and which of them this JVM does not export to it.
 * This class touches no javac internals, so it can be asked before {@link ConstantUnfolding} is loaded.
 */
public final class Exports {
    private static final String[] PACKAGES = {"api", "code", "tree", "util"};

    private Exports() {
    }

    /** The {@code --add-exports} options the JVM still needs for constant unfolding; empty if none. */
    public static List<String> missing() {
        Module javac = JavacTask.class.getModule();
        Module self = Exports.class.getModule();
        List<String> missing = new ArrayList<>();
        for (String p : PACKAGES) {
            String pkg = "com.sun.tools.javac." + p;
            if (!javac.isExported(pkg, self)) {
                missing.add("--add-exports=jdk.compiler/" + pkg + "=ALL-UNNAMED");
            }
        }
        return missing;
    }
}
