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

package org.techlook.http;

import org.techlook.http.client.HttpListener;

import java.nio.charset.Charset;
import java.util.List;

/**
 * Base interface comprises http methods. All methods are supposed to be asynchronous. Connecting to the remote server
 * doesn't places here it's assumed to be established for HTTP client itself and therefore specified methods
 * can be called multiple times using the same connection. The additional headers don't basic headers which
 * are required for appropriate HTTP method. For example the GET method adds Connection and
 * Accept-Encoding headers hence you shouldn't add they as additional headers.
 */
public interface Http {
    /**
     * HTTP GET method
     * @param url                the request URL to be fetched
     * @param additionalHeaders  additional user defined HTTP headers
     * @param parameters         request parameters which will be added to the URL
     * @param listener           HTTP listener
     */
    void get(String url,
             List<Pair<String, String>> additionalHeaders,
             List<Pair<String, String>> parameters,
             HttpListener listener);

    /**
     * HTTP PUT method
     * @param url                the request URL
     * @param additionalHeaders  additional user defined HTTP headers
     * @param urlParameters      request parameters which will be added to the URL
     * @param contentType        MIME-type of the content
     * @param contentCharset     content charset
     * @param content            content
     * @param listener           HTTP listener
     */
    void put(String url,
             List<Pair<String, String>> additionalHeaders,
             List<Pair<String, String>> urlParameters,
             String contentType,
             Charset contentCharset,
             byte[] content,
             HttpListener listener);

    /**
     * HTTP POST method for sending arbitrary content. Request parameters will be added to URL.
     * @param url                the request URL
     * @param additionalHeaders  additional user defined HTTP headers
     * @param urlParameters      request parameters which will be added to the URL
     * @param contentType        MIME-type of the content
     * @param contentCharset     content charset
     * @param content            content
     * @param listener           HTTP listener
     */
    void postContent(String url,
                     List<Pair<String, String>> additionalHeaders,
                     List<Pair<String, String>> urlParameters,
                     String contentType,
                     Charset contentCharset,
                     byte[] content,
                     HttpListener listener);

    /**
     * HTTP POST method for sending encoded parameters in content (body).
     * @param url                the request URL
     * @param additionalHeaders  additional user defined HTTP headers
     * @param parameters         request parameters which will be encoded into the request body
     * @param listener           HTTP listener
     */
    void postWithEncodedParameters(String url,
                                   List<Pair<String, String>> additionalHeaders,
                                   List<Pair<String, String>> parameters,
                                   HttpListener listener);

    /**
     * HTTP POST method for sending form data in content (body).
     * @param url                the request URL
     * @param additionalHeaders  additional user defined HTTP headers
     * @param requestData        the form data to be sent
     * @param listener           HTTP listener
     */
    void postFormData(String url,
                      List<Pair<String, String>> additionalHeaders,
                      FormRequestData requestData,
                      HttpListener listener);

    /**
     * HTTP OPTIONS method with specified URL within the server
     * @param url       the request URL
     * @param listener  HTTP listener
     */
    void optionsWithUrl(String url, HttpListener listener);

    /**
     * HTTP OPTIONS method for obtain properties of the server itself
     * @param listener  HTTP listener
     */
    void options(HttpListener listener);
}
