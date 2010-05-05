/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.vm.compiler.snippet;

import com.sun.max.annotate.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.object.*;
import com.sun.max.vm.reference.*;

/**
 * Snippets that implement fast receiver-dependent method selection at runtime.
 *
 * @author Bernd Mathiske
 */
public abstract class MethodSelectionSnippet extends Snippet {

    private MethodSelectionSnippet() {
        super();
    }

    public static final class SelectVirtualMethod extends Snippet {
        /**
         * Meta-evaluates only up to the dynamic method actor, not all the way to the target method's entry point.
         */
        public static VirtualMethodActor quasiFold(Object receiver, VirtualMethodActor declaredMethod) {
            if (receiver == null) {
                throw new NullPointerException();
            }
            if (declaredMethod.isPrivate()) {
                return declaredMethod;
            }
            final Class receiverClass = receiver.getClass();
            final ClassActor classActor = ClassActor.fromJava(receiverClass);
            return classActor.getVirtualMethodActorByVTableIndex(declaredMethod.vTableIndex());
        }

        @INLINE
        public static Word selectNonPrivateVirtualMethod(Object receiver, VirtualMethodActor declaredMethod) {
            final Hub hub = ObjectAccess.readHub(receiver);
            return hub.getWord(declaredMethod.vTableIndex());
        }

        @SNIPPET
        @INLINE(afterSnippetsAreCompiled = true)
        public static Word selectVirtualMethod(Object receiver, VirtualMethodActor declaredMethod) {
            if (declaredMethod.isPrivate()) {
                // private methods do not have a vtable index, so dynamically compile the receiver.
                // this typically does not occur with javac generated bytecodes
                return CompilationScheme.Static.compile(declaredMethod, CallEntryPoint.VTABLE_ENTRY_POINT);
            }
            return selectNonPrivateVirtualMethod(receiver, declaredMethod);
        }

        public static final SelectVirtualMethod SNIPPET = new SelectVirtualMethod();
    }

    public static final class SelectInterfaceMethod extends MethodSelectionSnippet {
        /**
         * Meta-evaluates only up to the virtual method actor, not all the way to the target method's entry point.
         */
        public static VirtualMethodActor quasiFold(Object receiver, InterfaceMethodActor interfaceMethod) {
            final Class receiverClass = receiver.getClass();
            final ClassActor classActor = ClassActor.fromJava(receiverClass);
            if (MaxineVM.isHosted() && !VMConfiguration.target().bootCompilerScheme().areSnippetsCompiled()) {
                return classActor.findVirtualMethodActor(interfaceMethod);
            }
            final InterfaceActor interfaceActor = UnsafeCast.asInterfaceActor(interfaceMethod.holder());
            final int interfaceIIndex = classActor.dynamicHub().getITableIndex(interfaceActor.id) - classActor.dynamicHub().iTableStartIndex;
            return classActor.getVirtualMethodActorByIIndex(interfaceIIndex + interfaceMethod.iIndexInInterface());
        }

        public static final SelectInterfaceMethod SNIPPET = new SelectInterfaceMethod();

        @SNIPPET
        @INLINE(afterSnippetsAreCompiled = true)
        public static Word selectInterfaceMethod(Object receiver, InterfaceMethodActor interfaceMethod) {
            final Hub hub = ObjectAccess.readHub(receiver);
            final InterfaceActor interfaceActor = UnsafeCast.asInterfaceActor(interfaceMethod.holder());
            final int interfaceIndex = hub.getITableIndex(interfaceActor.id);
            return hub.getWord(interfaceIndex + interfaceMethod.iIndexInInterface());
        }
    }

    public static final class ReadHub extends MethodSelectionSnippet {

        @SNIPPET
        @INLINE(afterSnippetsAreCompiled = true)
        public static Reference readHub(Object object) {
            return Reference.fromJava(ObjectAccess.readHub(object));
        }
        public static final ReadHub SNIPPET = new ReadHub();
    }
}
