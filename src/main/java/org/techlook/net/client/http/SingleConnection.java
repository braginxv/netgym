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

import org.techlook.net.client.SocketClient;
import org.techlook.net.client.http.client.HttpAsyncClient;
import org.techlook.net.client.http.client.HttpListener;

import java.nio.charset.Charset;
import java.util.Set;

/**
 * Single connection establishes a TCP connection, performs a request, and closes the connection.
 */
public class SingleConnection implements HttpConnection {
    private final String server;
    private final int port;
    private final SocketClient asyncClient;

    /**
     * Constructor
     * @param server       a remote host
     * @param port         TCP port
     * @param asyncClient  asynchronous SocketClient instance used as a transport
     */
    public SingleConnection(String server, int port, SocketClient asyncClient) {
        this.server = server;
        this.port = port;
        this.asyncClient = asyncClient;
    }

    @Override
    public void head(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> parameters, HttpListener listener) {
        new HttpAsyncClient(server, port, false, asyncClient).head(url, additionalHeaders, parameters, listener);
    }

    @Override
    public void get(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> parameters, HttpListener listener) {
        new HttpAsyncClient(server, port, false, asyncClient).get(url, additionalHeaders, parameters, listener);
    }

    @Override
    public void put(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> urlParameters, String contentType, Charset contentCharset, byte[] content, HttpListener listener) {
        new HttpAsyncClient(server, port, false, asyncClient).put(url, additionalHeaders, urlParameters, contentType, contentCharset, content, listener);
    }

    @Override
    public void delete(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> urlParameters, String contentType, Charset contentCharset, byte[] content, HttpListener listener) {
        new HttpAsyncClient(server, port, false, asyncClient).delete(url, additionalHeaders, urlParameters, contentType, contentCharset, content, listener);
    }

    @Override
    public void patch(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> urlParameters, String contentType, Charset contentCharset, byte[] content, HttpListener listener) {
        new HttpAsyncClient(server, port, false, asyncClient).patch(url, additionalHeaders, urlParameters, contentType, contentCharset, content, listener);
    }

    @Override
    public void connect(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> parameters, HttpListener listener) {
        new HttpAsyncClient(server, port, false, asyncClient).connect(url, additionalHeaders, parameters, listener);
    }

    @Override
    public void trace(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> parameters, HttpListener listener) {
        new HttpAsyncClient(server, port, false, asyncClient).trace(url, additionalHeaders, parameters, listener);
    }

    @Override
    public void postContent(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> urlParameters, String contentType, Charset contentCharset, byte[] content, HttpListener listener) {
        new HttpAsyncClient(server, port, false, asyncClient).postContent(url, additionalHeaders, urlParameters, contentType, contentCharset, content, listener);
    }

    @Override
    public void postWithEncodedParameters(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> parameters, HttpListener listener) {
        new HttpAsyncClient(server, port, false, asyncClient).postWithEncodedParameters(url, additionalHeaders, parameters, listener);
    }

    @Override
    public void postFormData(String url, Set<Pair<String, String>> additionalHeaders, FormRequestData requestData, HttpListener listener) {
        new HttpAsyncClient(server, port, false, asyncClient).postFormData(url, additionalHeaders, requestData, listener);
    }

    @Override
    public void optionsWithUrl(String url,
                               Set<Pair<String, String>> headers,
                               Set<Pair<String, String>> urlParameters,
                               HttpListener listener) {
        new HttpAsyncClient(server, port, false, asyncClient)
                .optionsWithUrl(url, headers, urlParameters, listener);
    }

    @Override
    public void options(Set<Pair<String, String>> headers, HttpListener listener) {
        new HttpAsyncClient(server, port, false, asyncClient).options(headers, listener);
    }
}
