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

package org.techlook.http.adapters;

import org.techlook.Consumer;
import org.techlook.Either;
import org.techlook.ResultedCompletion;

/**
 * Use this class to get the entire response with string content,
 * blocking the user thread until it is completely received.
 */
public class SyncStringResponseListener extends StringResponseListener {
    private ResultedCompletion<StringResponse> completion = new ResultedCompletion<>();

    @Override
    public void respondString(Either<String, StringResponse> responseEither) {
        responseEither.right().apply(new Consumer<StringResponse>() {
            @Override
            public void consume(StringResponse response) {
                completion.finish(response);
            }
        });

        responseEither.left().apply(new Consumer<String>() {
            @Override
            public void consume(String message) {
                completion.failure(message);
            }
        });
    }

    /**
     * use this completion to get response synchronously
     * @return  completion of getting response
     */
    public ResultedCompletion<StringResponse> watchCompletion() {
        return completion;
    }
}
