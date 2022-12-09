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

package org.techlook.net.client.http.adapters;

import org.techlook.net.client.Either;
import org.techlook.net.client.Fault;
import org.techlook.net.client.http.client.HttpListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * This listener is used to get the whole byte content, not chunks.
 */
public abstract class ByteResponseListener extends HttpListener {
    private final ByteArrayOutputStream stream = new ByteArrayOutputStream();
    private int code;
    private String httpVersion;
    private String description;
    private Map<String, String> headers;

    @Override
    public final void responseCode(int code, String httpVersion, String description) {
        this.code = code;
        this.httpVersion = httpVersion;
        this.description = description;
    }

    @Override
    public final void respond(byte[] chunk) {
        try {
            synchronized (stream) {
                stream.write(chunk);
            }
        } catch (IOException e) {
            failure(Fault.ByteBufferFillError.format(e.getMessage()));
        }
    }

    @Override
    public final void complete() {
        Response response = new Response(code, httpVersion, description, headers, stream.toByteArray(), charset);
        respond(Either.<String, Response>right(response));
    }

    @Override
    public final void failure(String message) {
        respond(Either.<String, Response>left(message));
    }

    @Override
    public final void respondHttpHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    @Override
    public void connectionClosed() {
        if (code == 0) {
            respond(Either.<String, Response>left("No response"));
        } else {
            Response response = new Response(code, httpVersion, description, headers, null, charset);
            respond(Either.<String, Response>right(response));
        }
    }

    /**
     * Implement this method to obtain the whole byte response
     * @param response  byte response
     */
    public abstract void respond(Either<String, Response> response);
}
