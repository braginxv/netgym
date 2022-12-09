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
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * consists input and loadable file fields
 */
public class FormRequestData {
    private final List<FormField> fields = new LinkedList<>();

    /**
     * Add input form field containing a raw byte array data
     * @param name          name of the field
     * @param value         raw byte array data
     * @param contentType   MIME-type of the content, may be null
     * @param valueCharset  charset of the content, may be null
     */
    public void addInputField(String name, byte[] value, String contentType, Charset valueCharset) {
        fields.add(new FormField(name, value, contentType, valueCharset));
    }

    /**
     * Add an input form field with a string
     * @param name          name of a field
     * @param value         string value
     * @param contentType   MIME-type of a content, may be null
     * @param valueCharset  charset of a content, may be null
     */
    public void addInputField(String name, String value, String contentType, Charset valueCharset) {
        byte[] content = value.getBytes(valueCharset != null ? valueCharset : StandardCharsets.UTF_8);
        fields.add(new FormField(name, content, contentType, valueCharset));
    }

    /**
     * Add input form field with raw byte array content data
     * @param name          name of a field
     * @param fileName      file name
     * @param contentType   MIME-type of a content, may be null
     * @param fileContent   the file content itself, may be null
     * @param charset       charset of a content, may be null
     */
    public void addFileField(String name, String fileName, byte[] fileContent, String contentType, Charset charset) {
        fields.add(new FormFileField(name, fileName, fileContent, contentType, charset));
    }

    /**
     * @return  all added fields
     */
    public List<FormField> getFields() {
        return Collections.unmodifiableList(fields);
    }

    /**
     * @return true if there are no fields
     */
    public boolean isEmpty() {
        return fields.isEmpty();
    }
}
