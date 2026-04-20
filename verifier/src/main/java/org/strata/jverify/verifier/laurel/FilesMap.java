package org.strata.jverify.verifier.laurel;

import org.strata.jverify.common.Position;

import java.net.URI;

public interface FilesMap {
    Position computePositionFromFileOffset(URI file, int offset);
}
