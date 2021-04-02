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

/**
 * This class is used when the synchronous result is expected in user thread.
 */
public class Completion {
    private volatile boolean inProgress = true;
    private volatile int timeout;

    /**
     * Create Completion instance without waiting completion time interval
     */
    public Completion() {
    }

    /**
     * Create Completion instance with waiting completion time interval
     *
     * @param timeout is time interval user thread will continue running despite the completion hasn't been finished
     */
    public Completion(int timeout) {
        this.timeout = timeout;
    }

    /**
     * this method is called by subject that controls the finishing
     */
    public synchronized void finish() {
        inProgress = false;

        notifyAll();
    }

    /**
     * blocks user thread and waits completion
     *
     * @throws InterruptedException if waiting was interrupted
     */
    public synchronized void await() throws InterruptedException {
        if (inProgress) {
            if (timeout > 0) {
                wait(timeout);
            } else {
                wait();
            }
        }
    }

    /**
     * Check whether completion has done
     *
     * @return true if finished
     */
    public boolean isFinished() {
        return !inProgress;
    }
}
