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
 * The completion which finishing with the result
 */
public class ResultedCompletion<T> extends Completion {
    private Either<String, T> completionResult;

    /**
     * Create Completion instance without waiting completion time interval
     */
    public ResultedCompletion() {
        super();
    }

    /**
     * Create Completion instance with waiting completion time interval
     * @param timeout is time interval user thread will continue running despite the completion hasn't been finished
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
     * finish with specified result
     * @param result  result
     */
    public void finish(T result) {
        completionResult = Either.right(result);
        super.finish();
    }

    /**
     * failure with error message
     * @param message  error message
     */
    public void failure(String message) {
        completionResult = Either.left(message);
        super.finish();
    }

    /**
     * blocks thread until result appears
     * @return  Either of result
     * @throws InterruptedException  if the awaiting interrupted
     */
    public Either<String, T> awaitResult() throws InterruptedException {
        super.await();
        return completionResult;
    }
}
