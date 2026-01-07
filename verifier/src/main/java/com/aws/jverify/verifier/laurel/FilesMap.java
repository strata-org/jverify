package com.aws.jverify.verifier.laurel;

import com.aws.jverify.common.Position;

import java.net.URI;

public interface FilesMap {
    Position computePositionFromFileOffset(URI file, int offset);
}
