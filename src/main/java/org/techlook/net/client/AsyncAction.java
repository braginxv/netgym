/*
 * The MIT License
 *
 * Copyright (c) 2022 Vladimir Bragin
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

package org.techlook.net.client;

import org.techlook.net.client.nio.AsyncSocketClient;

import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This is the main stuff in the library to execute a bit of some work in the Fork-join Pool.
 * Computational intensive actions such as gzip deflate/inflate, TLS encrypt/decrypt are all AsyncAction.
 */
public abstract class AsyncAction implements Callable<Void> {
    /**
     * A coordinating queue designed to alleviate undesirable effects caused by different processing rates
     * in different Async Actions
     *
     * Do not set value less than 3
     *
     * A larger queue size entails a less synchronization between Actions, but leads to consuming of more memory
     */
    protected final ConcurrentLinkedQueue<ByteBuffer> chunks = new ConcurrentLinkedQueue<>();
    private static final int MAX_QUEUE_FILL = AsyncSocketClient.PARALLELISM_LEVEL * 5 / 2;

    private final AtomicBoolean shouldContinue = new AtomicBoolean();
    private final ForkJoinPool pool;
    private volatile ForkJoinTask<Void> completionTask;

    /**
     * Constructor
     *
     * @param pool Shared Fork-Join Pool
     */
    public AsyncAction(ForkJoinPool pool) {
        this.pool = pool;

        completionTask = new RecursiveAction() {
            @Override
            protected void compute() {
            }
        };
        completionTask.complete(null);
    }

    /**
     * Wake up the action to process a next bunch of chunks if the current portion has been processed.
     * If the queue size exceeds the max limit and processing of the previous portion of chunks hasn't been yet completed
     * then current thread waits a completion of the previous task using a work stealing algorithm in the Fork Join Pool
     *
     * @see ForkJoinPool
     */
    public void shakeUp() {
        shouldContinue.set(true);

        if (completionTask.isDone() || chunks.size() > MAX_QUEUE_FILL) {
            try {
                completionTask.get();
            } catch (InterruptedException ignored) {
            } catch (ExecutionException e) {
                throw new RuntimeException(e.getMessage());
            }
            shouldContinue.set(false);
            completionTask = pool.submit(this);
        }
    }

    @Override
    public Void call() {
        do {
            processAction();
        } while (!chunks.isEmpty() && shouldContinue.getAndSet(false));
        return null;
    }

    /**
     * blocks the current thread until all remaining chunks are processed
     */
    public void waitFinishing() {
        if (completionTask != null) {
            completionTask.join();
        }
        shakeUp();
        completionTask.join();
    }

    /**
     * @return true if the last task has been completed
     */
    public boolean isTaskCompleted() {
        return completionTask == null || completionTask.isDone();
    }

    /**
     * override this method to implement chunk processing logic
     */
    protected abstract void processAction();
}
