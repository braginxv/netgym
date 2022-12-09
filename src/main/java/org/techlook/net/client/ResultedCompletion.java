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

/**
 * Completion that ends with a result
 */
public class ResultedCompletion<T> extends Completion {
    private Either<String, T> completionResult;

    /**
     * Create Completion
     */
    public ResultedCompletion() {
        super();
    }

    /**
     * Create Completion with a waiting timeout
     *
     * @param timeout is a timeout
     */
    public ResultedCompletion(int timeout) {
        super(timeout);
    }

    /**
     * finch with null result
     */
    @Override
    public void finish() {
        completionResult = Either.right(null);
        super.finish();
    }

    /**
     * this method is called by the asynchronous operation
     */
    public void finish(T result) {
        completionResult = Either.right(result);
        super.finish();
    }

    /**
     * failure with an error message
     * @param message  error message
     */
    public void failure(String message) {
        completionResult = Either.left(message);
        super.finish();
    }

    /**
     * blocks thread until the result appears
     * @return  Either of the result
     * @throws InterruptedException  if the awaiting interrupts
     */
    public Either<String, T> awaitResult() throws InterruptedException {
        super.await();
        return completionResult;
    }
}
