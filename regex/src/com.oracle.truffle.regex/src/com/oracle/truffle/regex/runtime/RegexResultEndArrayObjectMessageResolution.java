/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.regex.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.result.LazyCaptureGroupsResult;
import com.oracle.truffle.regex.result.RegexResult;
import com.oracle.truffle.regex.result.SingleIndexArrayResult;
import com.oracle.truffle.regex.result.SingleResult;
import com.oracle.truffle.regex.result.SingleResultLazyStart;
import com.oracle.truffle.regex.result.StartsEndsIndexArrayResult;
import com.oracle.truffle.regex.result.TraceFinderResult;
import com.oracle.truffle.regex.runtime.RegexResultEndArrayObjectMessageResolutionFactory.RegexResultGetEndNodeGen;
import com.oracle.truffle.regex.runtime.nodes.LazyCaptureGroupGetResultNode;
import com.oracle.truffle.regex.runtime.nodes.TraceFinderGetResultNode;

@MessageResolution(receiverType = RegexResultEndArrayObject.class)
public class RegexResultEndArrayObjectMessageResolution {

    abstract static class RegexResultGetEndNode extends Node {

        abstract int execute(RegexResult receiver, int groupNumber);

        @Specialization(guards = {"isNoMatch(receiver)"})
        int doNoMatch(@SuppressWarnings("unused") RegexResult receiver, int groupNumber) {
            return outOfBoundsException(groupNumber, 0);
        }

        static boolean isNoMatch(RegexResult receiver) {
            return receiver == RegexResult.NO_MATCH;
        }

        @Specialization(guards = {"groupNumber == 0"})
        int doSingleResult(SingleResult receiver, @SuppressWarnings("unused") int groupNumber) {
            return receiver.getEnd();
        }

        @Specialization(guards = {"groupNumber != 0"})
        int doSingleResultOutOfBounds(@SuppressWarnings("unused") SingleResult receiver, int groupNumber) {
            return outOfBoundsException(groupNumber, 1);
        }

        @Specialization(guards = {"groupNumber == 0"})
        int doSingleResultLazyStart(SingleResultLazyStart receiver, @SuppressWarnings("unused") int groupNumber) {
            return receiver.getEnd();
        }

        @Specialization(guards = {"groupNumber != 0"})
        int doSingleResultLazyStartOutOfBounds(@SuppressWarnings("unused") SingleResultLazyStart receiver, int groupNumber) {
            return outOfBoundsException(groupNumber, 1);
        }

        @Specialization
        int doStartsEndsIndexArray(StartsEndsIndexArrayResult receiver, int groupNumber) {
            return receiver.getEnds()[groupNumber];
        }

        @Specialization
        int doSingleIndexArray(SingleIndexArrayResult receiver, int groupNumber) {
            return fromSingleArray(receiver.getIndices(), groupNumber);
        }

        @Specialization
        int doTraceFinder(TraceFinderResult receiver, int groupNumber,
                        @Cached("create()") TraceFinderGetResultNode getResultNode) {
            return fromSingleArray(getResultNode.execute(receiver), groupNumber);
        }

        @Specialization
        int doLazyCaptureGroups(LazyCaptureGroupsResult receiver, int groupNumber,
                        @Cached("create()") LazyCaptureGroupGetResultNode getResultNode) {
            return fromSingleArray(getResultNode.execute(receiver), groupNumber) - 1;
        }

        private static int fromSingleArray(int[] array, int groupNumber) {
            return array[groupNumber * 2 + 1];
        }

        public static RegexResultGetEndNode create() {
            return RegexResultGetEndNodeGen.create();
        }
    }

    private static int outOfBoundsException(int groupNumber, int size) {
        CompilerDirectives.transferToInterpreter();
        throw new IndexOutOfBoundsException(String.format("index: %d, size: %d", groupNumber, size));
    }

    @Resolve(message = "READ")
    abstract static class RegexResultEndReadNode extends Node {

        @Child RegexResultGetEndNode getEndNode = RegexResultGetEndNode.create();

        public Object access(RegexResultEndArrayObject receiver, int index) {
            return getEndNode.execute(receiver.getResult(), index);
        }
    }

    @Resolve(message = "HAS_SIZE")
    abstract static class RegexResultEndHasSizeNode extends Node {

        @SuppressWarnings("unused")
        public boolean access(RegexResultEndArrayObject receiver) {
            return true;
        }
    }

    @Resolve(message = "GET_SIZE")
    abstract static class RegexResultEndGetSizeNode extends Node {

        public int access(RegexResultEndArrayObject receiver) {
            return receiver.getResult().getGroupCount();
        }
    }
}
