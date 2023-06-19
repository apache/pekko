/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

/*
 * Copyright (c) 2010-2011 Dmitry Vyukov. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 *    conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list
 *    of conditions and the following disclaimer in the documentation and/or other materials
 *    provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY DMITRY VYUKOV "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL DMITRY VYUKOV OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those of the authors and
 * should not be interpreted as representing official policies, either expressed or implied, of Dmitry Vyukov.
 */

package org.apache.pekko.dispatch;

import org.apache.pekko.util.Unsafe;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock-free MPSC linked queue implementation based on Dmitriy Vyukov's non-intrusive MPSC queue:
 *   - https://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue
 *   - Source code is covered by the Simplified BSD License and by Apache License, Version 2.0.
 *   - Apache Pekko chooses to use it under the Apache License, Version 2.0.
 *
 * This queue could be wait-free (i.e. without the spinning loops in peekNode and pollNode) if
 * it were permitted to return null while the queue is not quite empty anymore but the enqueued
 * element is not yet visible. This would break actor scheduling, though.
 */
@SuppressWarnings("serial")
public abstract class AbstractNodeQueue<T> extends AtomicReference<AbstractNodeQueue.Node<T>> {

    /*
     * Extends AtomicReference for the "head" slot (which is the one that is appended to) since
     * there is nothing to be gained by going to all-out Unsafe usage—we’d have to do
     * cache-line padding ourselves.
     */
  
    @SuppressWarnings("unused")
    private volatile Node<T> _tailDoNotCallMeDirectly;

    protected AbstractNodeQueue() {
       final Node<T> n = new Node<T>();
       _tailDoNotCallMeDirectly = n;
       set(n);
    }

    /**
     * Query the queue tail for the next element without dequeuing it.
     * 
     * Use this method only from the consumer thread!
     * 
     * !!! There is a copy of this code in pollNode() !!!
     * 
     * @return queue node with element inside if there was one, or null if there was none
     */
    @SuppressWarnings("unchecked")
    protected final Node<T> peekNode() {
        final Node<T> tail = ((Node<T>)Unsafe.instance.getObjectVolatile(this, tailOffset));
        Node<T> next = tail.next();
        if (next == null && get() != tail) {
            // if tail != head this is not going to change until producer makes progress
            // we can avoid reading the head and just spin on next until it shows up
            do {
                next = tail.next();
            } while (next == null);
        }
        return next;
    }
    
    /**
     * Query the queue tail for the next element without dequeuing it.
     * 
     * Use this method only from the consumer thread!
     * 
     * @return element if there was one, or null if there was none
     */
    public final T peek() {
        final Node<T> n = peekNode();
        return (n != null) ? n.value : null;
    }

    /**
     * Add an element to the head of the queue.
     * 
     * This method can be used from any thread.
     * 
     * @param value the element to be added; must not be null
     */
    public final void add(final T value) {
        final Node<T> n = new Node<T>(value);
        getAndSet(n).setNext(n);
    }
    
    /**
     * Add an element to the head of the queue, providing the queue node to be used.
     * 
     * This method can be used from any thread.
     * 
     * @param n the node containing the element to be added; both must not be null
     */
    public final void addNode(final Node<T> n) {
        n.setNext(null);
        getAndSet(n).setNext(n);
    }

    /**
     * Query the queue whether it is empty right now.
     * 
     * This method can be used from any thread.
     * 
     * @return true if queue was empty at some point in the past
     */
    public final boolean isEmpty() {
        return Unsafe.instance.getObjectVolatile(this, tailOffset) == get();
    }

    /**
     * This method returns an upper bound on the queue size at the time it
     * starts executing. It may spuriously return smaller values (including
     * zero) if the consumer pulls items out concurrently.
     * 
     * This method can be used from any thread.
     * 
     * @return an upper bound on queue length at some time in the past
     */
    @SuppressWarnings("unchecked")
    public final int count() {
        int count = 0;
        final Node<T> head = get();
        for(Node<T> n = ((Node<T>) Unsafe.instance.getObjectVolatile(this, tailOffset)).next();
            n != null && count < Integer.MAX_VALUE; 
            n = n.next()) {
          ++count;
          // only iterate up to the point where head was when starting: this is a moving queue!
          if (n == head) break;
        }
        return count;
    }

    /**
     * Pull one item from the queue’s tail if there is one.
     * 
     * Use this method only from the consumer thread!
     * 
     * @return element if there was one, or null if there was none
     */
    public final T poll() {
        final Node<T> next = pollNode();
        if (next == null) return null;
        else {
          T value = next.value;
          next.value = null;
          return value;
        }
    }
    
    /**
     * Pull one item from the queue, returning it within a queue node.
     * 
     * Use this method only from the consumer thread!
     * 
     * @return queue node with element inside if there was one, or null if there was none
     */
    @SuppressWarnings("unchecked")
    public final Node<T> pollNode() {
      final Node<T> tail = (Node<T>) Unsafe.instance.getObjectVolatile(this, tailOffset);
      Node<T> next = tail.next();
      if (next == null && get() != tail) {
          // if tail != head this is not going to change until producer makes progress
          // we can avoid reading the head and just spin on next until it shows up
          do {
              next = tail.next();
          } while (next == null);
      }
      if (next == null) return null;
      else {
        tail.value = next.value;
        next.value = null;
        Unsafe.instance.putOrderedObject(this, tailOffset, next);
        tail.setNext(null);
        return tail;
      }
    }

    private final static long tailOffset;

    static {
        try {
          tailOffset = Unsafe.instance.objectFieldOffset(AbstractNodeQueue.class.getDeclaredField("_tailDoNotCallMeDirectly"));
        } catch(Throwable t){
            throw new ExceptionInInitializerError(t);
        }
    }

    public static class Node<T> {
        public T value;
        @SuppressWarnings("unused")
        private volatile Node<T> _nextDoNotCallMeDirectly;

        public Node() {
            this(null);
        }

        public Node(final T value) {
            this.value = value;
        }

        @SuppressWarnings("unchecked")
        public final Node<T> next() {
            return (Node<T>)Unsafe.instance.getObjectVolatile(this, nextOffset);
        }

        protected final void setNext(final Node<T> newNext) {
          Unsafe.instance.putOrderedObject(this, nextOffset, newNext);
        }
        
        private final static long nextOffset;
        
        static {
            try {
                nextOffset = Unsafe.instance.objectFieldOffset(Node.class.getDeclaredField("_nextDoNotCallMeDirectly"));
            } catch(Throwable t){
                throw new ExceptionInInitializerError(t);
            } 
        }
    } 
}
