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
package com.sun.max.vm.monitor.modal.sync;

import com.sun.max.vm.*;
import com.sun.max.vm.bytecode.*;
import com.sun.max.vm.debug.*;
import com.sun.max.vm.monitor.modal.sync.nat.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.thread.*;

/**
 * Provides Java monitor services on behalf of a {@linkplain #boundObject() bound} object.
 *
 * The {@link Bytecode#MONITORENTER} and {@link Bytecode#MONITOREXIT} instructions are implemented via a per-monitor
 * mutex. {@linkplain Object#wait() Wait} and {@linkplain Object#notify() notify} are implemented via a per-monitor
 * waiting list and a per-thread {@linkplain VmThread#waitingCondition() condition variable} on which a thread suspends
 * itself. A per-thread condition variable is necessary in order to implement single thread notification.
 *
 * @author Simon Wilkinson
 */
public class StandardJavaMonitor extends AbstractJavaMonitor {

    protected final Mutex _mutex;
    private VmThread _waitingThreads;

    public StandardJavaMonitor() {
        _mutex = new Mutex();
    }

    private static void raiseIllegalMonitorStateException(VmThread owner) {
        if (owner == null) {
            throw new IllegalMonitorStateException();
        }
        throw new IllegalMonitorStateException("Monitor owned by thread \"" + owner.getName() + "\" [id=" + owner.threadMapID() + "]");
    }

    @Override
    public void monitorEnter() {
        final VmThread currentThread = VmThread.current();
        traceStartMonitorEnter(currentThread);
        if (_ownerThread == currentThread) {
            _recursionCount++;
            traceEndMonitorEnter(currentThread);
            return;
        }
        currentThread.setState(Thread.State.BLOCKED);
        _mutex.lock();
        currentThread.setState(Thread.State.RUNNABLE);
        _bindingProtection = BindingProtection.PROTECTED;
        _ownerThread = currentThread;
        _recursionCount = 1;
        traceEndMonitorEnter(currentThread);
    }

    @Override
    public void monitorExit() {
        final VmThread currentThread = VmThread.current();
        traceStartMonitorExit(currentThread);
        if (_ownerThread != currentThread) {
            raiseIllegalMonitorStateException(_ownerThread);
        }
        if (--_recursionCount == 0) {
            _bindingProtection = BindingProtection.UNPROTECTED;
            _ownerThread = null;
            traceEndMonitorExit(currentThread);
            _mutex.unlock();
        }
    }

    @Override
    public void monitorWait(long timeoutMilliSeconds) throws InterruptedException {
        final VmThread currentThread = VmThread.current();
        traceStartMonitorWait(currentThread);
        if (_ownerThread != currentThread) {
            raiseIllegalMonitorStateException(_ownerThread);
        }
        final int recursionCount = _recursionCount;
        final VmThread ownerThread = _ownerThread;
        if (timeoutMilliSeconds == 0L) {
            ownerThread.setState(Thread.State.WAITING);
        } else {
            ownerThread.setState(Thread.State.TIMED_WAITING);
        }

        final ConditionVariable waitingCondition  = _ownerThread.waitingCondition();
        if (waitingCondition.requiresAllocation()) {
            waitingCondition.allocate();
        }
        ownerThread.setNextWaitingThread(_waitingThreads);
        _waitingThreads = ownerThread;
        _ownerThread = null;
        final boolean interrupted = !waitingCondition.threadWait(_mutex, timeoutMilliSeconds);
        _ownerThread = ownerThread;
        ownerThread.setState(Thread.State.RUNNABLE);
        _recursionCount = recursionCount;

        boolean timedOut = false;
        if (!interrupted) {
            if (ownerThread.nextWaitingThread() != ownerThread) {
                // The thread is still on the _waitingThreads list: remove it
                timedOut = true;

                if (ownerThread == _waitingThreads) {
                    // Common case: owner is at the head of the list
                    _waitingThreads = ownerThread.nextWaitingThread();
                    ownerThread.setNextWaitingThread(ownerThread);
                } else {
                    if (_waitingThreads == null) {
                        FatalError.unexpected("Thread woken from wait by timeout not in waiting threads list");
                    }
                    // Must now search the list and remove ownerThread
                    VmThread previous = _waitingThreads;
                    VmThread waiter = previous.nextWaitingThread();
                    while (waiter != ownerThread) {
                        if (waiter == null) {
                            FatalError.unexpected("Thread woken from wait by timeout not in waiting threads list");
                        }
                        previous = waiter;
                        waiter = waiter.nextWaitingThread();
                    }
                    // ownerThread
                    previous.setNextWaitingThread(ownerThread.nextWaitingThread());
                    ownerThread.setNextWaitingThread(ownerThread);
                }
            }
        }

        traceEndMonitorWait(currentThread, interrupted, timedOut);

        if (interrupted || _ownerThread.isInterrupted(true)) {
            throw new InterruptedException();
        }
    }

    @Override
    public void monitorNotify(boolean all) {
        final VmThread currentThread = VmThread.current();
        traceStartMonitorNotify(currentThread);
        if (_ownerThread != currentThread) {
            raiseIllegalMonitorStateException(_ownerThread);
        }
        if (all) {
            VmThread waiter = _waitingThreads;
            while (waiter != null) {
                waiter.setState(Thread.State.BLOCKED);
                waiter.waitingCondition().threadNotify(false);
                final VmThread previous = waiter;
                waiter = waiter.nextWaitingThread();

                // This is the idiom for indicating that the thread is no longer on a waiters list.
                // which in turn makes it easy to determine in 'monitorWait' if the thread was
                // notified or woke up because the timeout expired.
                previous.setNextWaitingThread(previous);
            }
            _waitingThreads = null;
        } else {
            final VmThread waiter = _waitingThreads;
            if (waiter != null) {
                waiter.setState(Thread.State.BLOCKED);
                waiter.waitingCondition().threadNotify(false);
                _waitingThreads = waiter.nextWaitingThread();

                // See comment above.
                waiter.setNextWaitingThread(waiter);
            }
        }
        traceEndMonitorNotify(currentThread);
    }

    @Override
    public void monitorPrivateAcquire(VmThread owner, int lockQty) {
        FatalError.unexpected("Cannot perform a private monitor acquire from a " + this.getClass().getName());
    }

    @Override
    public void monitorPrivateRelease() {
        FatalError.unexpected("Cannot perform a private monitor release from a " + this.getClass().getName());
    }

    @Override
    public void allocate() {
        _mutex.alloc();
    }

    @Override
    public void dump() {
        super.dump();
        Log.print(" mutex=");
        Log.print(_mutex.asPointer());
        Log.print(" waiters={");
        VmThread waiter = _waitingThreads;
        while (waiter != null) {
            Log.print(waiter.getName());
            Log.print(" ");
            waiter = waiter.nextWaitingThread();
        }
        Log.print("}");
    }

    /**
     * Specialised JavaMonitor intended to be bound to the
     * VMThreadMap.ACTIVE object at image build time.
     *
     * MonitorEnter semantics are slightly modified to
     * halt a meta-circular regression arising from thread termination clean-up.
     * See VmThread.beTerminated().
     *
     * @author Simon Wilkinson
     */
    static class VMThreadMapJavaMonitor extends StandardJavaMonitor {

        @Override
        public void monitorEnter() {
            final VmThread currentThread = VmThread.current();
            if (currentThread.state() == Thread.State.TERMINATED) {
                assert _ownerThread != currentThread;
                _mutex.lock();
                _ownerThread = currentThread;
                _recursionCount = 1;
            } else {
                super.monitorEnter();
            }
        }
    }

    /**
     * Specialised JavaMonitor intended to be bound to the HeapScheme object at image build time.
     *
     * This monitor checks for the GC thread attempting to acquire the HeapScheme lock, which is
     * deadlock prone.
     *
     * @author Simon Wilkinson
     */
    static class HeapSchemeDeadlockDetectionJavaMonitor extends StandardJavaMonitor {

        private boolean _elideForDeadlockStackTrace = false;

        @Override
        public void monitorEnter() {
            final VmThread currentThread = VmThread.current();
            if (currentThread.isGCThread()) {
                if (currentThread.waitingCondition() == null) {
                    // This is the GC thread creating its private waiting condition variable.
                    // This done at VM boot so no deadlock risk.
                } else if (_elideForDeadlockStackTrace) {
                    // Pretend the GC thread has acquired the lock so that we can allocate if necessary while dumping its stack.
                    return;
                } else {
                    heapSchemeDeadlock();
                }
            }
            super.monitorEnter();
        }

        private void heapSchemeDeadlock() throws FatalError {
            Log.println("WARNING : GC thread is going for the HeapScheme lock. Trying to allocate?");
            Log.println("WARNING : Eliding HeapScheme lock for GC thread and attempting stack trace...");
            DebugBreak.here();
            _elideForDeadlockStackTrace = true;
            throw FatalError.unexpected("GC thread is attempting to allocate. Attempting stack trace.");
        }

        @Override
        public void monitorExit() {
            if (_elideForDeadlockStackTrace) {
                // Pretend the GC thread has released the lock so that we can allocate if necessary while dumping its stack.
                return;
            }
            super.monitorExit();
        }
    }
}
