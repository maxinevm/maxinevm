/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.max.graal.compiler.lir;

import java.util.*;

import com.oracle.max.asm.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiTargetMethod.*;
import com.sun.cri.ri.*;
import com.sun.cri.xir.*;
import com.sun.cri.xir.CiXirAssembler.*;

public class StandardOp {
    // Checkstyle: stop
    public static MoveOpcode<?, ?> MOVE;
    public static NullCheckOpcode<?, ?> NULL_CHECK;
    public static CallOpcode<?, ?> DIRECT_CALL;
    public static CallOpcode<?, ?> INDIRECT_CALL;
    public static ReturnOpcode<?, ?> RETURN;
    public static XirOpcode<?, ?> XIR;
    // Checkstyle: resume

    public interface MoveOpcode<A extends AbstractAssembler, I extends LIRInstruction> extends LIROpcode<A, I> {
        LIRInstruction create(CiValue result, CiValue input);
    }

    public interface NullCheckOpcode<A extends AbstractAssembler, I extends LIRInstruction> extends LIROpcode<A, I> {
        LIRInstruction create(CiVariable input, LIRDebugInfo info);
    }

    public interface CallOpcode<A extends AbstractAssembler, I extends LIRInstruction> extends LIROpcode<A, I> {
        LIRInstruction create(Object target, CiValue result, List<CiValue> arguments, CiValue targetAddress, LIRDebugInfo info, Map<XirMark, Mark> marks, List<CiValue> pointerSlots);
    }

    public interface ReturnOpcode<A extends AbstractAssembler, I extends LIRInstruction> extends LIROpcode<A, I> {
        LIRInstruction create(CiValue input);
    }

    public interface XirOpcode<A extends AbstractAssembler, I extends LIRInstruction> extends LIROpcode<A, I> {
        LIRInstruction create(XirSnippet snippet, CiValue[] operands, CiValue outputOperand, int tempInputCount, int tempCount, CiValue[] inputOperands, int[] operandIndices, int outputOperandIndex,
                        LIRDebugInfo info, LIRDebugInfo infoAfter, RiMethod method, List<CiValue> pointerSlots);
    }
}
