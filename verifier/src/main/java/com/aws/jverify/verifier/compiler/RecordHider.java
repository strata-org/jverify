package com.aws.jverify.verifier.compiler;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.sun.tools.javac.code.Flags.RECORD;
import static com.sun.tools.javac.code.Flags.SYNTHETIC;
import static com.sun.tools.javac.code.Flags.NATIVE;

public class RecordHider extends TreeTranslator {

    protected static final Context.Key<RecordHider> key = new Context.Key<>();

    private final Set<Symbol> records = new HashSet<>();

    public static RecordHider instance(Context context) {
        RecordHider instance = context.get(key);
        if (instance == null)
            instance = new RecordHider(context);
        return instance;
    }

    public RecordHider(Context context) {
        context.put(key, this);
    }

    private enum Direction {
        HIDE,
        REVEAL
    }
    private Direction direction;

    public <T extends JCTree> T hide(T tree) {
        direction = Direction.HIDE;
        T result = translate(tree);
        direction = null;
        return result;
    }

    public <T extends JCTree> T reveal(T tree) {
        direction = Direction.REVEAL;
        T result = translate(tree);
        direction = null;
        return result;
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl tree) {
        switch (direction) {
            case HIDE -> {
                if ((tree.mods.flags & RECORD) != 0) {
                    records.add(tree.sym);
                    tree.mods.flags &= ~RECORD;
                }
            }
            case Direction.REVEAL -> {
                if (records.contains(tree.sym)) {
                    tree.mods.flags |= RECORD;
                }
            }
        }
        super.visitClassDef(tree);
    }
}
