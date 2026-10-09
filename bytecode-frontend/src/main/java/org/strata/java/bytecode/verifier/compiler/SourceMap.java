package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.common.Position;
import org.strata.jverify.common.Range;
import org.strata.jverify.laurel.FileRange;
import org.strata.jverify.laurel.Raw;
import org.strata.jverify.laurel.SourceRange;
import org.strata.jverify.laurel.Uri;
import org.strata.java.bytecode.frontend.BytecodeFrontend.ClassSource;
import org.strata.java.bytecode.frontend.CrtRange;

import java.net.URI;
import java.util.List;

/**
 * Per-file positional service. Turns BYTECODE-NATIVE source positions --
 * {@code CharacterRangeTable} line:col spans and {@code LineNumberTable}
 * lines -- into Laurel {@link FileRange}s and reporting {@link Range}s.
 *
 * <p>The bytecode frontend never reads {@code .java} text: it only ever knows
 * a (line, column) or (line) from classfile debug attributes. A position
 * travels through Strata's byte-offset-shaped pipeline by being packed into a
 * synthetic offset by {@link PositionCodec} on the way out and unpacked on the
 * way back ({@link #positionRange} / the driver's {@code PositionResolver}), so no
 * source file and no real byte offset is ever needed.
 *
 * <p>Because spans are statement-level, sub-expression anchors (a method name,
 * a class keyword, an argument or lambda body) are not recoverable from
 * bytecode; callers use the enclosing statement/method range or NO_RANGE
 * instead.
 */
final class SourceMap {

    static final FileRange UNKNOWN_RANGE =
            new FileRange(new Uri("<unknown>"), new SourceRange(new Raw(0), new Raw(0)));

    /** The current file's URI (the SourceFile attribute name matched to an input path). */
    URI uri;
    /** Default range for nodes without their own position: the enclosing statement's. */
    FileRange defaultRange = UNKNOWN_RANGE;

    /** Match a class to its source file by the SourceFile attribute name (a name, not text). */
    static URI sourceUriFor(ClassSource cls, Iterable<URI> sourceUris) {
        if (cls.sourceFileName() == null) {
            return null;
        }
        String internal = cls.internalName();
        int slash = internal.lastIndexOf('/');
        String qualified = (slash < 0 ? "" : internal.substring(0, slash + 1)) + cls.sourceFileName();
        URI byName = null;
        for (URI uri : sourceUris) {
            String path = uri.getPath();
            if (path == null) {
                continue;
            }
            if (path.endsWith("/" + qualified)) {
                return uri;
            }
            if (byName == null && path.endsWith("/" + cls.sourceFileName())) {
                byName = uri;
            }
        }
        return byName;
    }

    // --- Ranges from bytecode line:col, via the synthetic-offset codec ---

    /** A FileRange spanning (beginLine:beginCol)..(endLine:endCol), all 1-indexed. */
    FileRange rangeOf(int beginLine, int beginCol, int endLine, int endCol) {
        if (uri == null) {
            return UNKNOWN_RANGE;
        }
        return new FileRange(new Uri(uri.toString()), new SourceRange(
                new Raw(PositionCodec.encode(beginLine, beginCol)),
                new Raw(PositionCodec.encode(endLine, endCol))));
    }

    /**
     * A point range at (line, 1): the LineNumberTable fallback stamp for input
     * without statement spans (line precision, no column).
     */
    FileRange lineRange(int line) {
        if (line < 1) {
            return UNKNOWN_RANGE;
        }
        return rangeOf(line, 1, line, 1);
    }

    /** The (beginLine:1)..(endLine:1) envelope of an inclusive 1-indexed line span. */
    FileRange lineSpanRange(int beginLine, int endLine) {
        if (beginLine < 1 || endLine < beginLine) {
            return UNKNOWN_RANGE;
        }
        return rangeOf(beginLine, 1, endLine, 1);
    }

    /** Decode a FileRange's synthetic offsets back into a reporting {@link Range}. */
    Range positionRange(FileRange range) {
        if (range == UNKNOWN_RANGE) {
            return Reporter.NO_RANGE;
        }
        return new Range(PositionCodec.decode(range.range().start().byteIdx()),
                PositionCodec.decode(range.range().stop().byteIdx()));
    }

    /** A 1-character point range at the start of {@code range}, or NO_RANGE. */
    Range pointOf(FileRange range) {
        if (range == UNKNOWN_RANGE) {
            return Reporter.NO_RANGE;
        }
        var start = PositionCodec.decode(range.range().start().byteIdx());
        return new Range(start, new Position(start.line(), start.character() + 1));
    }

    /**
     * The method's CRT envelope, from its Code attribute. Its START is the
     * method's declaration line (the outermost CRT block opens at the method's
     * {@code {}) -- where method-level diagnostics anchor (pinned by the corpus).
     */
    FileRange methodCrtRange(java.lang.classfile.MethodModel m) {
        var code = m.findAttribute(java.lang.classfile.Attributes.code()).orElse(null);
        if (code == null) {
            return UNKNOWN_RANGE;
        }
        var attr = (java.lang.classfile.attribute.CodeAttribute) code;
        var ranges = org.strata.java.bytecode.frontend.MethodBodyFactory.crtEnvelopeRanges(attr);
        // javac starts a lambda-bearing statement's CRT span at the CLASS header
        // (`13:01-88:76` for a postcondition on line 88), which dragged the method's
        // envelope -- and every method-level diagnostic -- onto the class line. The
        // method's own body block is right, so anything beginning before it is dropped.
        org.strata.java.bytecode.frontend.Span body = null;
        for (var e : org.strata.java.bytecode.frontend.MethodBodyFactory.crtEntries(attr)) {
            if (e.is(org.strata.java.bytecode.frontend.MethodBodyFactory.CrtEntry.BLOCK)
                    && !e.is(org.strata.java.bytecode.frontend.MethodBodyFactory.CrtEntry.STATEMENT)
                    && (body == null || before(e.span(), body))) {
                body = e.span();
            }
        }
        if (body != null) {
            var start = body;
            ranges = ranges.stream().filter(r -> !before(r.span(), start)).toList();
        }
        return methodRangeOf(ranges);
    }

    /** {@code a} begins strictly before {@code b}. */
    private static boolean before(org.strata.java.bytecode.frontend.Span a,
                                  org.strata.java.bytecode.frontend.Span b) {
        return a.beginLine() != b.beginLine() ? a.beginLine() < b.beginLine() : a.beginCol() < b.beginCol();
    }

    /**
     * A 1-character point range at (line, 1), or NO_RANGE for a non-positive
     * line. Matches {@link #pointOf}'s one-column width so class-level anchors
     * (which come through here) render the same caret as method-level ones.
     */
    Range linePoint(int line) {
        if (line < 1) {
            return Reporter.NO_RANGE;
        }
        return new Range(new Position(line, 1), new Position(line, 2));
    }

    /**
     * The min..max envelope over a method's statement-flagged CRT spans,
     * comparing line:col via the codec (line-major, so the packed offsets
     * order exactly as (line, col) pairs do).
     */
    FileRange methodRangeOf(List<CrtRange> crt) {
        if (crt == null || uri == null) {
            return UNKNOWN_RANGE;
        }
        long min = Long.MAX_VALUE;
        long max = -1;
        for (var r : crt) {
            var span = r.span();
            if (span.isNone()) {
                continue;
            }
            min = Math.min(min, PositionCodec.encode(span.beginLine(), span.beginCol()));
            max = Math.max(max, PositionCodec.encode(span.endLine(), span.endCol()));
        }
        if (max < 0) {
            return UNKNOWN_RANGE;
        }
        return new FileRange(new Uri(uri.toString()), new SourceRange(new Raw(min), new Raw(max)));
    }
}
