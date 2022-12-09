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

package org.techlook.net.client.http.client;

import org.techlook.net.client.Fault;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for receive http messages.
 */
public abstract class HttpListener {
    private final static String CONTENT_TYPE = "content-type";
    private final static Pattern CHARSET_PATTERN = Pattern.compile("charset\\s*=\\s*([^\\s;]+)");

    protected Charset charset;

    /**
     * Once the HTTP headers are parsed they falls here, then a charset is picked.
     * @param headers  headers in key-value format
     */
    public final void respondHeaders(Map<String, String> headers) {
        if (headers.containsKey(CONTENT_TYPE)) {
            String value = headers.get(CONTENT_TYPE);

            Matcher matcher = CHARSET_PATTERN.matcher(value);
            if (matcher.find()) {
                String charsetValue = matcher.group(1);

                if (charsetValue != null) try {
                    charset = Charset.forName(charsetValue);
                } catch (Exception e) {
                    failure(Fault.BadEncoding.format(charsetValue));
                    return;
                }
            }
        }

        respondHttpHeaders(headers);
    }

    /**
     * The response status consisting (code, HTTP version, description)
     * @param code         HTTP response code
     * @param httpVersion  HTTP version
     * @param description  description
     */
    public abstract void responseCode(int code, String httpVersion, String description);

    /**
     * is called when a byte chunk is received
     * @param chunk  transmitting data chunk
     */
    public abstract void respond(byte[] chunk);

    /**
     * occurs when something went wrong while receiving a response
     * @param message  error message
     */
    public void failure(String message) {}

    /**
     * receiving of a response is completed successfully
     */
    public abstract void complete();

    /**
     * The content charset from HTTP response headers
     * @return charset
     */
    public Charset getCharset() {
        return charset;
    }

    /**
     * send HTTP headers down the inheritance hierarchy
     * @param headers  headers in key-value format
     */
    public void respondHttpHeaders(Map<String, String> headers) {}

    /**
     * calls when connection is closed
     */
    public abstract void connectionClosed();
}
