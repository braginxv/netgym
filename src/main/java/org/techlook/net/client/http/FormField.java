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

package org.techlook.net.client.http;

import java.nio.charset.Charset;

/**
 * HTTP form field data
 */
public class FormField {
    private final byte[] value;
    protected String contentDisposition;
    protected String contentTypeSpec;

    /**
     * @param name         name of the form field
     * @param value        value of the form field
     * @param contentType  MIME-type of the content, may be null
     * @param charset      content charset, may be null
     */
    public FormField(String name, byte[] value, String contentType, Charset charset) {
        this.value = value;
        contentDisposition = "Content-Disposition: form-data; name=\"" + name + "\"";
        if (contentType != null) {
            contentTypeSpec = "Content-Type: " + contentType +
                    (charset != null ? "; charset=" + charset.name() : "");
        }
    }

    /**
     * Getter for header
     * @return header
     */
    public String header() {
        return String.format("%s\n%s\n", contentDisposition, contentTypeSpec);
    }

    /**
     * Getter for body
     * @return body
     */
    public byte[] body() {
        return value;
    }
}
