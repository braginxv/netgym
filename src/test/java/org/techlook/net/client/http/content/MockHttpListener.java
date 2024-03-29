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

package org.techlook.net.client.http.content;

import org.techlook.net.client.ResultedCompletion;
import org.techlook.net.client.http.client.HttpListener;

public class MockHttpListener extends HttpListener {
    private final StringBuilder response = new StringBuilder();
    private final ResultedCompletion<String> completion;


    public MockHttpListener(ResultedCompletion<String> completion) {
        this.completion = completion;
    }

    @Override
    public void responseCode(int code, String httpVersion, String description) {
    }

    @Override
    public void respond(byte[] content) {
        if (charset != null) {
            response.append(new String(content, charset));
        } else {
            response.append(new String(content));
        }
    }

    @Override
    public void complete() {
        completion.finish(response.toString());
    }

    @Override
    public void connectionClosed() {
        completion.finish();
    }

    @Override
    public void failure(String message) {
        completion.failure(message);
    }
}
