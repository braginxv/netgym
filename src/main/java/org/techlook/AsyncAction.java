/*
 * The MIT License
 *
 * Copyright (c) 2021 Vladimir Bragin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.techlook;

import org.techlook.nio.AsyncSocketClient;

import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Basic class to perform processing through chunks during the networking phase
 */
public abstract class AsyncAction implements Callable<Void> {
    /**
     * tune it, do not set value less than 3
     * The larger queue size produces higher parallelism level for the various stages of connection,
     * but it consumes more memory
     */
    private static final int MAX_QUEUE_FILL = AsyncSocketClient.PARALLELISM_LEVEL * 3 / 2;

    /**
     * incoming chunks for further processing
     */
    protected final ConcurrentLinkedQueue<ByteBuffer> chunks = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean isItInitialStart = new AtomicBoolean(true);
    private final AtomicBoolean shouldContinue = new AtomicBoolean();
    private final ForkJoinPool pool;
    private volatile ForkJoinTask<Void> completionTask;
    private final Object lock = new Object();

    /**
     * Creation
     * @param pool Common ForkJoinPoll withing client
     */
    public AsyncAction(ForkJoinPool pool) {
        this.pool = pool;
    }

    /**
     * Wake up that action to process next bunch of chunks if it has completed and now isn't running.
     * If chunks queue exceeds set max limit and processing the previous portion of chunks hasn't been processed yet
     * then current thread waits completion of the previous task using the work stealing algorithm in Fork Join Pool
     * @see ForkJoinPool
     */
    public void shakeUp() {
        shouldContinue.set(true);
        if (isItInitialStart.getAndSet(false)) {
            completionTask = pool.submit(this);
            return;
        }

        if (chunks.size() > MAX_QUEUE_FILL || completionTask.isDone()) {
            synchronized (lock) {
                if (chunks.size() > MAX_QUEUE_FILL || completionTask.isDone()) {
                    try {
                        completionTask.get();
                    } catch (InterruptedException ignored) {
                    } catch (ExecutionException e) {
                        throw new RuntimeException(e.getCause());
                    }
                    shouldContinue.set(false);
                    completionTask = pool.submit(this);
                }
            }
        }
    }

    /**
     * used by Thread Pool to fork new processing task
     * @return nothing
     */
    @Override
    public Void call() {
        do {
            processAction();
        } while (!chunks.isEmpty() && shouldContinue.getAndSet(false));
        return null;
    }

    /**
     * blocks current thread until all remaining chunks to be processed
     */
    public void waitFinishing() {
        if (completionTask != null) {
            completionTask.join();
        }
        shakeUp();
        completionTask.join();
    }

    /**
     * test whether last processing task has been completed
     * @return true if last processing task has been completed
     */
    public boolean isTaskCompleted() {
        return completionTask == null || completionTask.isDone();
    }

    /**
     * override this method to implement chunks processing payload
     */
    protected abstract void processAction();
}
