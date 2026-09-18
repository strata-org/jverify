package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.common.Range;
import org.strata.jverify.laurel.FileRange;
import org.strata.jverify.laurel.Raw;
import org.strata.jverify.laurel.SourceRange;
import org.strata.jverify.laurel.Uri;
import org.strata.java.bytecode.frontend.BytecodeFrontend.ClassSource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.strata.java.bytecode.frontend.CrtRange;

/**
 * Per-file positional service: the current source file's text and line
 * index, plus every helper that turns CRT spans / lexical anchors into
 * Laurel {@link FileRange}s and reporting {@link Range}s.
 *
 * <p>Extracted verbatim from {@code BytecodeToLaurelCompiler}; the
 * {@code current*} fields became {@link #uri}/{@link #source}/{@link #index}
 * and the default statement range became {@link #defaultRange}.
 */
final class SourceMap {

    static final FileRange UNKNOWN_RANGE =
            new FileRange(new Uri("<unknown>"), new SourceRange(new Raw(0), new Raw(0)));

    final Map<URI, LineIndex> lineIndexes = new HashMap<>();
    final Map<URI, String> sourceTexts = new HashMap<>();

    // Per-file state
    URI uri;
    String source;
    LineIndex index;
    /** Default range for nodes without their own position: the enclosing statement's. */
    FileRange defaultRange = UNKNOWN_RANGE;

    String sourceOf(URI uri) {
        return sourceTexts.get(uri);
    }

    /** Match a class to its source file by the SourceFile attribute name. */
    static URI sourceUriFor(ClassSource cls, Iterable<URI> sourceUris) {
        if (cls.sourceFileName() == null) {
            return null;
        }
        for (URI uri : sourceUris) {
            String path = uri.getPath();
            if (path != null && path.endsWith("/" + cls.sourceFileName())) {
                return uri;
            }
        }
        return null;
    }

    // --- Ranges from CRT spans ---

    FileRange rangeOf(int startOffset, int endOffset) {
        return new FileRange(new Uri(uri.toString()),
                new SourceRange(new Raw(startOffset), new Raw(endOffset)));
    }

    /**
     * Narrow a spec-call statement's range to its argument text:
     * {@code precondition(lo <= hi);} -> the {@code lo <= hi} range.
     * Falls back to the whole statement range when the shape is unexpected.
     */
    FileRange argumentRange(FileRange callRange) {
        if (source == null || callRange == UNKNOWN_RANGE) {
            return callRange;
        }
        int start = (int) callRange.range().start().byteIdx();
        int end = (int) callRange.range().stop().byteIdx();
        int open = source.indexOf('(', start);
        if (open < 0 || open >= end) {
            return callRange;
        }
        int depth = 0;
        int close = -1;
        for (int i = open; i < end; i++) {
            char c = source.charAt(i);
            if (c == '(') depth++;
            if (c == ')' && --depth == 0) {
                close = i;
                break;
            }
        }
        if (close < 0) {
            return callRange;
        }
        int argStart = open + 1;
        int argEnd = close;
        while (argStart < argEnd && Character.isWhitespace(source.charAt(argStart))) argStart++;
        while (argEnd > argStart && Character.isWhitespace(source.charAt(argEnd - 1))) argEnd--;
        return rangeOf(argStart, argEnd);
    }

    /**
     * The source range of a lambda's body inside a spec-call statement:
     * everything after the first {@code ->} up to (excluding) the spec call's
     * closing parenthesis. {@code postcondition((int res) -> res > 0)} ->
     * the {@code res > 0} range. Single-line spec calls only (the CRT gives
     * no sub-statement structure to do better with).
     */
    FileRange lambdaBodyRange(FileRange callRange) {
        if (source == null || callRange == UNKNOWN_RANGE) {
            return callRange;
        }
        int start = (int) callRange.range().start().byteIdx();
        int end = (int) callRange.range().stop().byteIdx();
        int arrow = source.indexOf("->", start);
        if (arrow < 0 || arrow >= end) {
            return argumentRange(callRange);
        }
        int bodyStart = arrow + 2;
        while (bodyStart < end && Character.isWhitespace(source.charAt(bodyStart))) {
            bodyStart++;
        }
        // callRange ends at the spec call's ')' (semicolon already stripped).
        int bodyEnd = end - 1;
        while (bodyEnd > bodyStart && Character.isWhitespace(source.charAt(bodyEnd - 1))) {
            bodyEnd--;
        }
        return rangeOf(bodyStart, bodyEnd);
    }

    Range positionRange(FileRange range) {
        if (index == null || range == UNKNOWN_RANGE) {
            return Reporter.NO_RANGE;
        }
        return new Range(index.position((int) range.range().start().byteIdx()),
                index.position((int) range.range().stop().byteIdx()));
    }

    /**
     * The span envelope of a method for the DIRECT translator: min..max over
     * its statement-flagged CRT entries (the AST-free equivalent of
     * {@link #methodRange(MethodDeclaration)}, whose statements carried
     * exactly these spans).
     */
    /** The method's CRT span envelope, from its Code attribute. */
    FileRange methodCrtRange(java.lang.classfile.MethodModel m) {
        var code = m.findAttribute(java.lang.classfile.Attributes.code()).orElse(null);
        if (code == null) {
            return UNKNOWN_RANGE;
        }
        return methodRangeOf(org.strata.java.bytecode.frontend.MethodBodyFactory.crtRanges(
                (java.lang.classfile.attribute.CodeAttribute) code));
    }

    FileRange methodRangeOf(java.util.List<CrtRange> crt) {
        if (index == null || crt == null) {
            return UNKNOWN_RANGE;
        }
        int min = Integer.MAX_VALUE;
        int max = -1;
        for (var r : crt) {
            var span = r.span();
            if (span.isNone()) {
                continue;
            }
            min = Math.min(min, index.offset(span.beginLine(), span.beginCol()));
            max = Math.max(max, index.offset(span.endLine(), span.endCol()));
        }
        if (max < 0) {
            return UNKNOWN_RANGE;
        }
        return rangeOf(min, max);
    }

    /**
     * The 1-char method-name anchor for the DIRECT translator: same lexical
     * recovery as {@link #namePointRange(MethodDeclaration)}, keyed by name +
     * declaration ordinal + the method's span envelope instead of the AST.
     */
    Range namePointRangeOf(String methodName, int ordinal, FileRange range) {
        if (source == null) {
            return Reporter.NO_RANGE;
        }
        var pattern = java.util.regex.Pattern
                .compile("\\b" + java.util.regex.Pattern.quote(methodName) + "\\s*\\(");
        int namePos = -1;
        if (range == UNKNOWN_RANGE) {
            var matcher = pattern.matcher(source);
            for (int i = 0; matcher.find(); i++) {
                if (i == ordinal) {
                    namePos = matcher.start();
                    break;
                }
            }
            if (namePos < 0) {
                return Reporter.NO_RANGE;
            }
        } else {
            int bodyStart = (int) range.range().start().byteIdx();
            var matcher = pattern.matcher(source);
            while (matcher.find() && matcher.start() < bodyStart) {
                namePos = matcher.start();
            }
            if (namePos < 0) {
                return positionRange(range);
            }
        }
        var start = index.position(namePos);
        return new Range(start, new org.strata.jverify.common.Position(start.line(), start.character() + 1));
    }

    // --- Lexical anchors (JFE's javac tree positions, recovered from text) ---

    /**
     * A 1-character range at the first occurrence of {@code needle} within
     * the current statement's source text -- how JFE's javac diagnostics
     * anchor at a sub-expression's own position, which the CRT's
     * statement-level spans cannot provide directly. Falls back to the
     * statement range when the text is not found.
     */
    Range pointRangeAt(String needle) {
        if (source == null || index == null
                || defaultRange == UNKNOWN_RANGE) {
            return Reporter.NO_RANGE;
        }
        int start = (int) defaultRange.range().start().byteIdx();
        int end = (int) defaultRange.range().stop().byteIdx();
        int at = source.indexOf(needle, start);
        if (at < 0 || at >= end) {
            return positionRange(defaultRange);
        }
        var pos = index.position(at);
        return new Range(pos, new org.strata.jverify.common.Position(pos.line(), pos.character() + 1));
    }

    /** True when the character at {@code offset} sits after a {@code //} on its line. */
    private boolean inLineComment(int offset) {
        int lineStart = source.lastIndexOf('\n', offset - 1) + 1;
        int comment = source.indexOf("//", lineStart);
        return comment >= 0 && comment < offset;
    }

    // --- Name-based anchor twins for the DIRECT (classfile-sourced) walk ---

    /** {@link #classKeywordPoint} keyed by the type's source name. */
    Range classKeywordPointOf(String typeName) {
        if (source == null) {
            return Reporter.NO_RANGE;
        }
        var matcher = java.util.regex.Pattern
                .compile("\\b(?:class|interface|record)\\s+"
                        + java.util.regex.Pattern.quote(typeName) + "\\b")
                .matcher(source);
        while (matcher.find()) {
            if (!inLineComment(matcher.start())) {
                var start = index.position(matcher.start());
                return new Range(start,
                        new org.strata.jverify.common.Position(start.line(), start.character() + 1));
            }
        }
        return Reporter.NO_RANGE;
    }

    /** {@link #annotationPoint} keyed by the type's source name. */
    Range annotationPointOf(String typeName, String annotationName) {
        if (source == null) {
            return Reporter.NO_RANGE;
        }
        var kw = java.util.regex.Pattern
                .compile("\\b(?:class|interface|record)\\s+"
                        + java.util.regex.Pattern.quote(typeName) + "\\b")
                .matcher(source);
        int limit = kw.find() ? kw.start() : source.length();
        var matcher = java.util.regex.Pattern
                .compile("@" + java.util.regex.Pattern.quote(annotationName) + "\\b")
                .matcher(source);
        int pos = -1;
        while (matcher.find() && matcher.start() < limit) {
            if (!inLineComment(matcher.start())) {
                pos = matcher.start();
            }
        }
        if (pos < 0) {
            return classKeywordPointOf(typeName);
        }
        var start = index.position(pos);
        return new Range(start, new org.strata.jverify.common.Position(start.line(), start.character() + 1));
    }

    /** {@link #contractMethodNamePoint} with a precomputed span anchor. */
    Range contractMethodNamePointOf(String methodName, Range anchored) {
        if (anchored != Reporter.NO_RANGE) {
            return anchored;
        }
        if (source == null) {
            return Reporter.NO_RANGE;
        }
        var matcher = java.util.regex.Pattern
                .compile("\\b" + java.util.regex.Pattern.quote(methodName) + "\\s*\\(")
                .matcher(source);
        if (!matcher.find()) {
            return Reporter.NO_RANGE;
        }
        var start = index.position(matcher.start());
        return new Range(start, new org.strata.jverify.common.Position(start.line(), start.character() + 1));
    }

    /** {@link #bodylessNamePoint} keyed by the method's name. */
    Range bodylessNamePointOf(String methodName) {
        if (source == null || index == null) {
            return Reporter.NO_RANGE;
        }
        var matcher = java.util.regex.Pattern
                .compile("\\b" + java.util.regex.Pattern.quote(methodName)
                        + "\\s*\\([^)]*\\)\\s*;")
                .matcher(source);
        while (matcher.find()) {
            int at = matcher.start();
            if (at > 0 && source.charAt(at - 1) == '.') {
                continue; // a call through a receiver, not a declaration
            }
            var start = index.position(at);
            return new Range(start,
                    new org.strata.jverify.common.Position(start.line(), start.character() + 1));
        }
        return Reporter.NO_RANGE;
    }

    /** {@link #classKeywordRange} keyed by the type's source name. */
    Range classKeywordRangeOf(String typeName) {
        if (source == null || index == null) {
            return Reporter.NO_RANGE;
        }
        var matcher = java.util.regex.Pattern
                .compile("\\b(class|interface|enum|record)\\s+"
                        + java.util.regex.Pattern.quote(typeName) + "\\b")
                .matcher(source);
        if (!matcher.find()) {
            return Reporter.NO_RANGE;
        }
        var start = index.position(matcher.start());
        return new Range(start, new org.strata.jverify.common.Position(start.line(), start.character() + 1));
    }
}
