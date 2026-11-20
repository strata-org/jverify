package org.strata.java.bytecode.frontend;

import java.lang.constant.ClassDesc;

/** One exception-table entry, resolved to blocks. type == null means catch-all (finally / any). */
public record Trap(int startBlk, int endBlkId, int handlerBlk, ClassDesc type) { }
