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

import org.techlook.net.client.http.client.HttpListener;

import java.nio.charset.Charset;
import java.util.Set;

/**
 * An interface containing methods for making HTTP requests. All methods are assumed to be asynchronous.
 */
public interface HttpConnection {
    /**
     * HTTP HEAD method
     * @param url                request URL
     * @param additionalHeaders  user-defined HTTP headers
     * @param parameters         request parameters to be url-encoded  
     * @param listener           HTTP listener
     */
    void head(String url,
             Set<Pair<String, String>> additionalHeaders,
             Set<Pair<String, String>> parameters,
             HttpListener listener);

    /**
     * HTTP GET method
     * @param url                request URL
     * @param additionalHeaders  user-defined HTTP headers
     * @param parameters         request parameters to be url-encoded
     * @param listener           HTTP listener
     */
    void get(String url,
             Set<Pair<String, String>> additionalHeaders,
             Set<Pair<String, String>> parameters,
             HttpListener listener);

    /**
     * HTTP PUT method
     * @param url                request URL
     * @param additionalHeaders  user-defined HTTP headers
     * @param urlParameters      request parameters to be url-encoded
     * @param contentType        MIME-type of a content
     * @param contentCharset     content charset
     * @param content            content
     * @param listener           HTTP listener
     */
    void put(String url,
             Set<Pair<String, String>> additionalHeaders,
             Set<Pair<String, String>> urlParameters,
             String contentType,
             Charset contentCharset,
             byte[] content,
             HttpListener listener);

    /**
     * HTTP DELETE method
     * @param url                the request URL
     * @param additionalHeaders  user-defined HTTP headers
     * @param urlParameters      request parameters to be url-encoded
     * @param contentType        MIME-type of a content (optional)
     * @param contentCharset     content charset (optional)
     * @param content            content (optional)
     * @param listener           HTTP listener
     */
    void delete(String url,
             Set<Pair<String, String>> additionalHeaders,
             Set<Pair<String, String>> urlParameters,
             String contentType,
             Charset contentCharset,
             byte[] content,
             HttpListener listener);

    /**
     * HTTP PATCH method
     * @param url                the request URL
     * @param additionalHeaders  user-defined HTTP headers
     * @param urlParameters      request parameters to be url-encoded
     * @param contentType        MIME-type of a content
     * @param contentCharset     content charset
     * @param content            content
     * @param listener           HTTP listener
     */
    void patch(String url,
               Set<Pair<String, String>> additionalHeaders,
               Set<Pair<String, String>> urlParameters,
               String contentType,
               Charset contentCharset,
               byte[] content,
               HttpListener listener);

    /**
     * HTTP CONNECT method
     * @param url                the request URL to be fetched
     * @param additionalHeaders  user-defined HTTP headers
     * @param parameters         request parameters to be url-encoded
     * @param listener           HTTP listener
     */
    void connect(String url,
             Set<Pair<String, String>> additionalHeaders,
             Set<Pair<String, String>> parameters,
             HttpListener listener);

    /**
     * HTTP TRACE method
     * @param url                the request URL to be fetched
     * @param additionalHeaders  user-defined HTTP headers
     * @param parameters         request parameters to be url-encoded
     * @param listener           HTTP listener
     */
    void trace(String url,
              Set<Pair<String, String>> additionalHeaders,
              Set<Pair<String, String>> parameters,
              HttpListener listener);

    /**
     * HTTP POST method
     * @param url                the request URL
     * @param additionalHeaders  user-defined HTTP headers
     * @param urlParameters      request parameters to be url-encoded
     * @param contentType        MIME-type of a content
     * @param contentCharset     content charset
     * @param content            content
     * @param listener           HTTP listener
     */
    void postContent(String url,
                     Set<Pair<String, String>> additionalHeaders,
                     Set<Pair<String, String>> urlParameters,
                     String contentType,
                     Charset contentCharset,
                     byte[] content,
                     HttpListener listener);

    /**
     * HTTP POST method sending url-encoded parameters in its body
     * @param url                the request URL
     * @param additionalHeaders  user-defined HTTP headers
     * @param parameters         request parameters which will be encoded into the request body
     * @param listener           HTTP listener
     */
    void postWithEncodedParameters(String url,
                                   Set<Pair<String, String>> additionalHeaders,
                                   Set<Pair<String, String>> parameters,
                                   HttpListener listener);

    /**
     * HTTP POST method that sends multipart form data
     * @param url                the request URL
     * @param additionalHeaders  user-defined HTTP headers
     * @param requestData        the form data to be sent
     * @param listener           HTTP listener
     */
    void postFormData(String url,
                      Set<Pair<String, String>> additionalHeaders,
                      FormRequestData requestData,
                      HttpListener listener);

    /**
     * HTTP OPTIONS
     * @param url       the request URL
     * @param listener  HTTP listener
     */
    void optionsWithUrl(String url,
                        Set<Pair<String, String>> headers,
                        Set<Pair<String, String>> urlParameters,
                        HttpListener listener);

    /**
     * HTTP OPTIONS method intending to get properties of a remote host
     * @param listener  HTTP listener
     */
    void options(
            Set<Pair<String, String>> headers,
            HttpListener listener);
}
