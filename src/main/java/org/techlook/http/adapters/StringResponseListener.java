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
import org.techlook.Fault;

import java.io.UnsupportedEncodingException;

/**
 * This listener is used to get the string content in its entirety, not in chunks.
 */
public abstract class StringResponseListener extends ByteResponseListener {
    @Override
    public final void respond(Either<String, Response> responseEither) {
        responseEither.left().apply(new Consumer<String>() {
            @Override
            public void consume(String value) {
                respondString(Either.<String, StringResponse>left(value));
            }
        });
        responseEither.right().apply(new Consumer<Response>() {
            @Override
            public void consume(final Response response) {
                String content;
                try {
                    if (response.getContent() == null) {
                        content = null;
                    } else {
                        content = charset != null ?
                                new String(response.getContent(), charset.name()) :
                                new String(response.getContent());
                    }
                } catch (UnsupportedEncodingException e) {
                    failure(Fault.BadEncoding.format(e.getMessage()));
                    return;
                }
                respondString(Either.<String, StringResponse>right(new StringResponse(response.getCode(),
                        response.getHttpVersion(), response.getResponseDescription(),
                        response.getHeaders(), content, response.getCharset())));
            }
        });
    }

    /**
     * Implement this method to obtain whole string response
     * @param response  string response
     */
    public abstract void respondString(Either<String, StringResponse> response);
}
