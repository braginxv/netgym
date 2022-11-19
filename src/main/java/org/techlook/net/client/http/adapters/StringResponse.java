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

import java.nio.charset.Charset;
import java.util.Map;

/**
 * Aggregated HTTP server response as string
 */
public class StringResponse {
    private final int code;
    private final String httpVersion;
    private final String responseDescription;
    private final Map<String, String> headers;
    private final String content;
    private final Charset charset;

    /**
     * @param code                 response code
     * @param httpVersion          version of HTTP
     * @param responseDescription  description
     * @param headers              response headers in key-value format
     * @param content              string content if it presented, may be null
     * @param charset              charset if it presented, may be null
     */
    public StringResponse(int code,
                    String httpVersion,
                    String responseDescription,
                    Map<String, String> headers,
                    String content,
                    Charset charset) {
        this.code = code;
        this.httpVersion = httpVersion;
        this.responseDescription = responseDescription;
        this.headers = headers;
        this.content = content;
        this.charset = charset;
    }

    public int getCode() {
        return code;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public String getResponseDescription() {
        return responseDescription;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getContent() {
        return content;
    }

    public Charset getCharset() {
        return charset;
    }
}
